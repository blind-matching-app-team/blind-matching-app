package com.bma.payment.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.payment.dto.PaymentDtos.ConsumablePurchaseRequest;
import com.bma.payment.dto.PaymentDtos.ConsumablePurchaseResponse;
import com.bma.payment.dto.PaymentDtos.ItemBalance;
import com.bma.payment.dto.PaymentDtos.PaymentResponse;
import com.bma.payment.dto.PaymentDtos.ProductResponse;
import com.bma.payment.entity.BillingKey;
import com.bma.payment.entity.ItemType;
import com.bma.payment.entity.Payment;
import com.bma.payment.entity.PaymentEvent;
import com.bma.payment.entity.Product;
import com.bma.payment.entity.UserItem;
import com.bma.payment.repository.BillingKeyRepository;
import com.bma.payment.repository.PaymentEventRepository;
import com.bma.payment.repository.PaymentRepository;
import com.bma.payment.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 상품 조회, 소모형 이용권 결제(CM-15), 빌링키 청구 공통 로직, 웹훅.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final String ORDER_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository eventRepository;
    private final BillingKeyRepository billingKeyRepository;
    private final PaymentGateway paymentGateway;
    private final ProductBenefits productBenefits;
    private final ItemWalletService walletService;
    private final BillingKeyCipher billingKeyCipher;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    /**
     * 판매 중인 상품 목록. 구매 모달(CM-13)이 소모형/구독형 탭으로 나눠 그린다.
     *
     * @return 상품 목록
     */
    public List<ProductResponse> getProducts() {
        return productRepository.findByUseYnAndDeletedOrderByIdAsc(YesNo.Y, YesNo.N).stream()
                .map(p -> ProductResponse.of(p, productBenefits.parse(p).raw()))
                .toList();
    }

    /**
     * 내 결제 내역(최신순).
     *
     * @param userId 사용자
     * @return 결제 목록
     */
    public List<PaymentResponse> getMyPayments(Long userId) {
        return paymentRepository.findByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /**
     * 소모형 이용권 단건 결제 (CM-15).
     *
     * <p>결제창 승인({@code paymentKey})이면 같은 주문 ID 재요청에 기존 결제를 그대로 돌려준다(멱등).
     * 저장 카드 청구면 서버가 주문 ID 를 만든다. 승인 금액은 항상 서버의 상품 가격과 대조한다.</p>
     *
     * @param userId  사용자
     * @param request 요청
     * @return 결제와 결제 후 잔여
     */
    // 승인 실패(PAY_002)를 던져도 결제 원장의 FAILED 행과 이벤트는 남긴다.
    @Transactional(noRollbackFor = BusinessException.class)
    public ConsumablePurchaseResponse purchaseConsumable(Long userId, ConsumablePurchaseRequest request) {
        Product product = findOnSale(request.productCode(), ProductBenefits.TYPE_ITEM);
        ProductBenefits.Benefit benefit = productBenefits.parse(product);
        ItemType itemType = ItemType.of(benefit.itemType());
        if (itemType == null || benefit.quantity() <= 0) {
            throw new IllegalStateException("소모형 상품의 혜택 구성이 잘못되었습니다: " + product.getProductCode());
        }

        Payment payment;
        if (request.hasWidgetPayment()) {
            if (request.orderId() == null || request.orderId().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제창 승인에는 주문 ID가 필요합니다.");
            }
            Payment existing = paymentRepository.findByOrderId(request.orderId()).orElse(null);
            if (existing != null) {
                if (!existing.getUserId().equals(userId)) {
                    // 남의 주문 ID로 결제를 가로채려는 시도.
                    log.warn("타인의 주문 ID로 결제 시도: userId={}, orderId={}", userId, request.orderId());
                    throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
                }
                if (existing.isApproved()) {
                    // 이미 승인·지급까지 끝난 주문. 이용권을 다시 주지 않는다.
                    log.info("이미 승인된 결제 재요청(멱등 반환): orderId={}", request.orderId());
                    return new ConsumablePurchaseResponse(PaymentResponse.from(existing), balances(userId));
                }
            }
            payment = existing != null ? existing
                    : paymentRepository.save(Payment.ready(userId, product, Payment.KIND_CONSUMABLE,
                    Payment.METHOD_WIDGET, request.orderId(), product.getProductName()));
            recordEvent(payment.getId(), PaymentEvent.TYPE_REQUEST, PaymentEvent.STATUS_SUCCESS,
                    null, toJson(request), null, null);
            approveWidget(payment, product, request.paymentKey());
        } else {
            payment = chargeWithBillingKey(userId, product, Payment.KIND_CONSUMABLE, null, product.getProductName());
        }

        walletService.addPurchased(userId, itemType, benefit.quantity(), payment.getId());
        log.info("소모형 결제 완료: userId={}, product={}, paymentId={}", userId, product.getProductCode(), payment.getId());
        return new ConsumablePurchaseResponse(PaymentResponse.from(payment), balances(userId));
    }

    /**
     * 저장된 빌링키로 청구한다. 구독 첫 결제·갱신과 소모형 저장카드 결제가 공유한다.
     *
     * <p>실패하면 결제 원장에 FAILED 로 남기고 {@link BusinessException}(PAY_002)을 던진다.
     * 호출자는 트랜잭션 경계를 어떻게 잡느냐에 따라 실패 행을 남길지 결정한다.</p>
     *
     * @param userId         사용자
     * @param product        상품
     * @param kind           결제 종류
     * @param subscriptionId 구독 청구면 구독 ID
     * @param orderName      주문명
     * @return 승인된 결제
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Payment chargeWithBillingKey(Long userId, Product product, String kind, Long subscriptionId,
                                        String orderName) {
        BillingKey key = billingKeyRepository.findByUserId(userId)
                .filter(BillingKey::isUsable)
                .orElseThrow(() -> new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND));

        Payment payment = paymentRepository.save(Payment.ready(userId, product, kind, Payment.METHOD_BILLING,
                newOrderId(userId), orderName));
        payment.setSubscriptionId(subscriptionId);
        recordEvent(payment.getId(), PaymentEvent.TYPE_REQUEST, PaymentEvent.STATUS_SUCCESS, null,
                "{\"method\":\"BILLING\",\"orderId\":\"" + payment.getOrderId() + "\"}", null, null);

        PaymentGateway.Approval approval;
        try {
            approval = paymentGateway.chargeBillingKey(billingKeyCipher.decrypt(key.getBillingKeyEnc()),
                    key.getCustomerKey(), payment.getOrderId(), orderName, product.getPrice());
        } catch (GatewayException e) {
            failPayment(payment, e.getCode(), e.getMessage(), null);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "결제에 실패했어요, 다시 시도해주세요. (" + e.getCode() + ")");
        }
        verifyAndApprove(payment, product, approval);
        return payment;
    }

    /**
     * 사용자의 빌링키를 발급·저장한다(기존 행이 있으면 새 카드로 덮어쓴다).
     *
     * @param userId 사용자
     * @param issued 게이트웨이가 발급한 빌링키
     * @return 저장된 행
     */
    @Transactional
    public BillingKey storeBillingKey(Long userId, PaymentGateway.IssuedBillingKey issued) {
        byte[] enc = billingKeyCipher.encrypt(issued.billingKey());
        BillingKey key = billingKeyRepository.findByUserId(userId)
                .map(existing -> {
                    existing.replace(issued.customerKey(), enc, issued.cardCompany(), issued.cardNumberMasked());
                    return existing;
                })
                .orElseGet(() -> billingKeyRepository.save(BillingKey.issue(userId, issued.customerKey(), enc,
                        issued.cardCompany(), issued.cardNumberMasked())));
        recordEvent(null, PaymentEvent.TYPE_REQUEST, PaymentEvent.STATUS_SUCCESS, null,
                "{\"type\":\"BILLING_KEY_ISSUE\",\"userId\":" + userId + "}", issued.rawResponse(), null);
        return key;
    }

    /**
     * 판매 중인 상품을 코드로 찾는다.
     *
     * @param productCode 코드
     * @param productType 기대하는 유형(ITEM/SUBSCRIPTION)
     * @return 상품
     */
    public Product findOnSale(String productCode, String productType) {
        return productRepository.findByProductCodeAndUseYnAndDeleted(productCode, YesNo.Y, YesNo.N)
                .filter(p -> productType.equals(p.getProductType()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 종류별 잔여(항상 2종 모두).
     *
     * @param userId 사용자
     * @return 잔여 목록
     */
    public List<ItemBalance> balances(Long userId) {
        Map<String, UserItem> byType = new java.util.HashMap<>();
        walletService.balances(userId).forEach(i -> byType.put(i.getItemType(), i));
        List<ItemBalance> result = new ArrayList<>();
        for (ItemType type : ItemType.values()) {
            UserItem item = byType.get(type.name());
            result.add(item == null ? ItemBalance.zero(type.name()) : ItemBalance.from(item));
        }
        return result;
    }

    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (!paymentGateway.verifyWebhookSignature(rawBody, signature)) {
            log.warn("웹훅 서명 검증 실패");
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        JsonNode payload = parseJson(rawBody);
        String eventId = textOrNull(payload, "eventId");
        String paymentKey = textOrNull(payload, "paymentKey");
        String status = textOrNull(payload, "status");
        // 토스 웹훅은 data.paymentKey / data.status 로 감싸서 온다.
        JsonNode data = payload.get("data");
        if (data != null && data.isObject()) {
            if (paymentKey == null) {
                paymentKey = textOrNull(data, "paymentKey");
            }
            if (status == null) {
                status = textOrNull(data, "status");
            }
        }

        // 같은 이벤트를 두 번 반영하지 않는다.
        if (eventId != null && eventRepository.existsByIdempotencyKey(eventId)) {
            log.info("이미 처리한 웹훅 이벤트(무시): eventId={}", eventId);
            return;
        }

        Payment payment = (paymentKey == null) ? null
                : paymentRepository.findByPaymentKey(paymentKey).orElse(null);

        if (payment == null) {
            // 결제 건을 찾지 못해도 200으로 응답해야 PG가 무한 재시도하지 않는다.
            // 대신 원문을 남겨 나중에 대사할 수 있게 한다.
            recordEvent(null, PaymentEvent.TYPE_WEBHOOK, PaymentEvent.STATUS_FAIL,
                    eventId, null, rawBody, "일치하는 결제 건 없음: paymentKey=" + paymentKey);
            log.warn("웹훅에 해당하는 결제 건을 찾을 수 없음: paymentKey={}", paymentKey);
            return;
        }

        applyWebhookStatus(payment, status);
        recordEvent(payment.getId(), PaymentEvent.TYPE_WEBHOOK, PaymentEvent.STATUS_SUCCESS,
                eventId, null, rawBody, null);

        log.info("웹훅 반영 완료: paymentId={}, status={}", payment.getId(), status);
    }

    private void approveWidget(Payment payment, Product product, String paymentKey) {
        PaymentGateway.Approval approval;
        try {
            approval = paymentGateway.approve(payment.getOrderId(), paymentKey, product.getPrice());
        } catch (GatewayException e) {
            failPayment(payment, e.getCode(), e.getMessage(), null);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "결제에 실패했어요, 다시 시도해주세요. (" + e.getCode() + ")");
        } catch (Exception e) {
            failPayment(payment, "GATEWAY_ERROR", e.getMessage(), null);
            log.error("결제 승인 중 게이트웨이 오류: orderId={}", payment.getOrderId(), e);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        verifyAndApprove(payment, product, approval);
    }

    private void verifyAndApprove(Payment payment, Product product, PaymentGateway.Approval approval) {
        if (!approval.isDone()) {
            failPayment(payment, "NOT_APPROVED", "PG 상태: " + approval.status(), approval.rawResponse());
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        // PG가 실제로 승인한 금액이 상품 가격과 같은지 반드시 대조한다.
        // compareTo를 쓰는 이유: BigDecimal의 equals는 소수점 스케일까지 비교하므로
        // 1000 과 1000.00 을 다른 값으로 판단한다.
        if (approval.amount() == null || approval.amount().compareTo(product.getPrice()) != 0) {
            failPayment(payment, "AMOUNT_MISMATCH",
                    "기대 금액=" + product.getPrice() + ", 승인 금액=" + approval.amount(), approval.rawResponse());
            log.error("결제 금액 불일치: orderId={}, 기대={}, 승인={}",
                    payment.getOrderId(), product.getPrice(), approval.amount());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        payment.approve(approval.paymentKey());
        recordEvent(payment.getId(), PaymentEvent.TYPE_APPROVE, PaymentEvent.STATUS_SUCCESS,
                null, null, approval.rawResponse(), null);
        log.info("결제 승인 완료: paymentId={}, orderId={}, amount={}",
                payment.getId(), payment.getOrderId(), payment.getAmount());
    }

    private void failPayment(Payment payment, String code, String message, String rawResponse) {
        payment.fail(code, message);
        recordEvent(payment.getId(), PaymentEvent.TYPE_APPROVE, PaymentEvent.STATUS_FAIL,
                null, null, rawResponse, code + ": " + message);
    }

    private void applyWebhookStatus(Payment payment, String status) {
        if (status == null) {
            return;
        }
        switch (status) {
            case Payment.STATUS_DONE -> {
                if (!payment.isApproved()) {
                    payment.approve(payment.getPaymentKey());
                }
            }
            case Payment.STATUS_CANCELED -> payment.cancel();
            case Payment.STATUS_FAILED -> payment.fail("WEBHOOK_FAILED", "PG가 실패를 통보했습니다.");
            default -> log.info("처리 대상이 아닌 웹훅 상태: {}", status);
        }
    }

    /**
     * 토스 규칙(6~64자, 영문/숫자/-/_)에 맞는 주문 ID 를 만든다.
     */
    private String newOrderId(Long userId) {
        StringBuilder sb = new StringBuilder("bma-").append(userId).append('-')
                .append(Long.toString(System.currentTimeMillis(), 36)).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ORDER_ID_ALPHABET.charAt(random.nextInt(ORDER_ID_ALPHABET.length())));
        }
        return sb.toString();
    }

    private void recordEvent(Long paymentId, String type, String status, String idempotencyKey,
                             String requestJson, String responseJson, String errorMessage) {
        eventRepository.save(PaymentEvent.of(
                paymentId, type, status, idempotencyKey, requestJson, safeJson(responseJson), errorMessage));
    }

    /** JSON 컬럼에는 JSON 만 넣을 수 있다. 원문이 JSON 이 아니면 문자열로 감싼다. */
    private String safeJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            objectMapper.readTree(value);
            return value;
        } catch (Exception e) {
            return toJson(value);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("결제 이벤트 직렬화 실패", e);
            return null;
        }
    }

    private JsonNode parseJson(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "웹훅 본문을 해석할 수 없습니다.");
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }
}
