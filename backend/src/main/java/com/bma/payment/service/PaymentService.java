package com.bma.payment.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.payment.dto.PaymentDtos.PaymentRequest;
import com.bma.payment.dto.PaymentDtos.PaymentResponse;
import com.bma.payment.dto.PaymentDtos.ProductResponse;
import com.bma.payment.entity.Payment;
import com.bma.payment.entity.PaymentEvent;
import com.bma.payment.entity.Product;
import com.bma.payment.repository.PaymentEventRepository;
import com.bma.payment.repository.PaymentRepository;
import com.bma.payment.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 승인 및 웹훅 처리.
 *
 * <p>고친 점 — 기존 {@code PaymentController}에는 결제 시스템의 기본이 빠져 있었다.</p>
 * <ul>
 *   <li><b>멱등성 없음</b>: 같은 주문으로 재요청하면 {@code UK_PY_PAYMENT_ORDER} 위반으로 500이었다.
 *       이제 기존 결제를 찾아 그대로 반환한다.</li>
 *   <li><b>금액 검증 없음</b>: PG가 실제로 승인한 금액을 확인하지 않았다.
 *       이제 상품 가격과 대조해 다르면 거부한다.</li>
 *   <li><b>웹훅이 빈 껍데기</b>: 본문을 받기만 하고 버렸으며, 인증이 필요한 경로에 있어
 *       PG 콜백이 401로 튕겼다. 이제 서명을 검증하고 결제 상태에 반영한다.</li>
 *   <li><b>감사 로그 없음</b>: {@code PY_PAYMENT_EVENT}에 요청/응답 원문을 남긴다.</li>
 *   <li>없는 상품이면 {@code orElseThrow()}로 500이 났다. 이제 404로 응답한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository eventRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    /**
     * 판매 중인 상품 목록을 조회한다.
     *
     * @return 상품 목록
     */
    public List<ProductResponse> getProducts() {
        return productRepository.findByUseYnAndDeletedOrderByIdAsc(YesNo.Y, YesNo.N).stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * 내 결제 내역을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 결제 목록(최신순)
     */
    public List<PaymentResponse> getMyPayments(Long userId) {
        return paymentRepository.findByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /**
     * 결제를 승인한다.
     *
     * @param userId  결제자
     * @param request 결제 요청
     * @return 결제 결과
     * @throws BusinessException 상품이 없거나, 주문 소유자가 다르거나, 금액이 어긋나거나, 승인이 실패한 경우
     */
    @Transactional
    public PaymentResponse pay(Long userId, PaymentRequest request) {
        Product product = productRepository
                .findByIdAndUseYnAndDeleted(request.productId(), YesNo.Y, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 멱등 처리: 같은 주문 ID가 이미 승인되었으면 그대로 반환한다.
        // 네트워크 재시도나 사용자의 중복 클릭으로 이중 결제가 발생하지 않게 한다.
        Payment existing = paymentRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(userId)) {
                // 남의 주문 ID로 결제를 가로채려는 시도.
                log.warn("타인의 주문 ID로 결제 시도: userId={}, orderId={}", userId, request.orderId());
                throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
            }
            if (existing.isApproved()) {
                log.info("이미 승인된 결제 재요청(멱등 반환): orderId={}", request.orderId());
                return PaymentResponse.from(existing);
            }
        }

        // 금액은 항상 서버가 상품 가격으로 결정한다. 클라이언트는 금액을 보내지 않는다.
        Payment payment = (existing != null) ? existing : paymentRepository.save(
                Payment.ready(userId, product.getId(), request.orderId(),
                        product.getPrice(), product.getCurrencyCode()));

        recordEvent(payment.getId(), PaymentEvent.TYPE_REQUEST, PaymentEvent.STATUS_SUCCESS,
                null, toJson(request), null, null);

        PaymentGateway.Approval approval;
        try {
            approval = paymentGateway.approve(request.orderId(), request.paymentKey(), product.getPrice());
        } catch (Exception e) {
            payment.fail("GATEWAY_ERROR", e.getMessage());
            recordEvent(payment.getId(), PaymentEvent.TYPE_APPROVE, PaymentEvent.STATUS_FAIL,
                    null, toJson(request), null, e.getMessage());
            log.error("결제 승인 중 게이트웨이 오류: orderId={}", request.orderId(), e);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        if (!approval.isDone()) {
            payment.fail("NOT_APPROVED", "PG 상태: " + approval.status());
            recordEvent(payment.getId(), PaymentEvent.TYPE_APPROVE, PaymentEvent.STATUS_FAIL,
                    null, toJson(request), approval.rawResponse(), "승인되지 않은 상태: " + approval.status());
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        // PG가 실제로 승인한 금액이 상품 가격과 같은지 반드시 대조한다.
        // compareTo를 쓰는 이유: BigDecimal의 equals는 소수점 스케일까지 비교하므로
        // 1000 과 1000.00 을 다른 값으로 판단한다.
        if (approval.amount() == null || approval.amount().compareTo(product.getPrice()) != 0) {
            payment.fail("AMOUNT_MISMATCH",
                    "기대 금액=" + product.getPrice() + ", 승인 금액=" + approval.amount());
            recordEvent(payment.getId(), PaymentEvent.TYPE_APPROVE, PaymentEvent.STATUS_FAIL,
                    null, toJson(request), approval.rawResponse(), "결제 금액 불일치");
            log.error("결제 금액 불일치: orderId={}, 기대={}, 승인={}",
                    request.orderId(), product.getPrice(), approval.amount());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        payment.approve(approval.paymentKey());
        recordEvent(payment.getId(), PaymentEvent.TYPE_APPROVE, PaymentEvent.STATUS_SUCCESS,
                null, toJson(request), approval.rawResponse(), null);

        log.info("결제 승인 완료: paymentId={}, orderId={}, amount={}",
                payment.getId(), payment.getOrderId(), payment.getAmount());
        return PaymentResponse.from(payment);
    }

    /**
     * PG 웹훅을 처리한다.
     *
     * <p>이 메서드는 인증되지 않은 외부 요청을 받으므로, 서명 검증이 유일한 신뢰 근거다.
     * 또한 PG는 같은 이벤트를 여러 번 보낼 수 있으므로 멱등성 키로 중복 처리를 막는다.</p>
     *
     * @param rawBody   요청 본문 원문(서명 검증을 위해 파싱 전 문자열이 필요하다)
     * @param signature 요청 헤더의 서명
     * @throws BusinessException 서명 검증에 실패한 경우
     */
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

    /**
     * 웹훅이 알려준 상태를 결제에 반영한다.
     *
     * @param payment 결제
     * @param status  PG 상태
     */
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
     * 결제 이벤트 로그를 남긴다.
     *
     * @param paymentId      결제 ID
     * @param type           이벤트 유형
     * @param status         처리 결과
     * @param idempotencyKey 멱등성 키
     * @param requestJson    요청 원문
     * @param responseJson   응답 원문
     * @param errorMessage   오류 메시지
     */
    private void recordEvent(Long paymentId, String type, String status, String idempotencyKey,
                             String requestJson, String responseJson, String errorMessage) {
        eventRepository.save(PaymentEvent.of(
                paymentId, type, status, idempotencyKey, requestJson, responseJson, errorMessage));
    }

    /**
     * 객체를 JSON 문자열로 변환한다. 감사 로그용이므로 실패해도 흐름을 막지 않는다.
     *
     * @param value 대상 객체
     * @return JSON 문자열
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("결제 이벤트 직렬화 실패", e);
            return null;
        }
    }

    /**
     * 웹훅 본문을 파싱한다.
     *
     * @param rawBody 본문 원문
     * @return JSON 노드
     * @throws BusinessException 파싱할 수 없는 본문인 경우
     */
    private JsonNode parseJson(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "웹훅 본문을 해석할 수 없습니다.");
        }
    }

    /**
     * JSON 노드에서 문자열 필드를 꺼낸다.
     *
     * @param node  JSON 노드
     * @param field 필드명
     * @return 값. 없으면 {@code null}
     */
    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }
}
