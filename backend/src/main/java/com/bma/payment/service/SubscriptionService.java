package com.bma.payment.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.payment.dto.PaymentDtos.RenewResult;
import com.bma.payment.dto.PaymentDtos.SubscriptionRequest;
import com.bma.payment.dto.PaymentDtos.SubscriptionResponse;
import com.bma.payment.dto.PaymentDtos.SubscriptionStatusResponse;
import com.bma.payment.entity.BillingKey;
import com.bma.payment.entity.Payment;
import com.bma.payment.entity.Product;
import com.bma.payment.entity.Subscription;
import com.bma.payment.repository.BillingKeyRepository;
import com.bma.payment.repository.ProductRepository;
import com.bma.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * 정밀매칭 구독 (CM-16/17, BMA-19 안건4).
 *
 * <ul>
 *   <li>등록: 빌링키 발급 → 첫 달 청구 → 구독 ACTIVE → 이번 달 이용권 지급</li>
 *   <li>해지: 다음 청구만 멈추고 남은 기간은 유지(환불 없음)</li>
 *   <li>갱신 배치: 청구일이 지난 구독을 청구하고 성공하면 다음 달 지급, 실패하면 유예 후 종료</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    /** 기본 구독 상품. */
    public static final String DEFAULT_PRODUCT_CODE = "PREMIUM_MONTHLY";

    private static final List<String> OPEN_STATUSES = List.of(
            Subscription.STATUS_ACTIVE, Subscription.STATUS_PAST_DUE, Subscription.STATUS_CANCELED);

    private final SubscriptionRepository subscriptionRepository;
    private final ProductRepository productRepository;
    private final BillingKeyRepository billingKeyRepository;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final ProductBenefits productBenefits;
    private final SubscriptionRenewalWorker renewalWorker;

    /**
     * 구독을 등록한다 (CM-17 '구독하기').
     *
     * @param userId  사용자
     * @param request 결제 수단
     * @return 구독 상세
     */
    // 첫 달 청구 실패(PAY_002)여도 저장한 카드와 FAILED 결제 행은 남긴다. 구독 행은 청구 성공 후에만 만든다.
    @Transactional(noRollbackFor = BusinessException.class)
    public SubscriptionResponse subscribe(Long userId, SubscriptionRequest request) {
        subscriptionRepository.findFirstByUserIdAndSubStatusInAndDeletedOrderByIdDesc(userId, OPEN_STATUSES, YesNo.N)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
                });

        String productCode = request.productCode() == null || request.productCode().isBlank()
                ? DEFAULT_PRODUCT_CODE : request.productCode();
        Product product = paymentService.findOnSale(productCode, ProductBenefits.TYPE_SUBSCRIPTION);
        ProductBenefits.Benefit benefit = productBenefits.parse(product);

        BillingKey key = resolveBillingKey(userId, request);

        // 첫 달 청구. 실패하면 PAY_002 가 올라가고 구독은 만들어지지 않는다(결제 원장에는 FAILED 로 남는다).
        Payment payment = paymentService.chargeWithBillingKey(userId, product, Payment.KIND_SUBSCRIPTION, null,
                product.getProductName() + " (첫 달)");

        LocalDate today = LocalDate.now();
        Subscription subscription = subscriptionRepository.save(
                Subscription.start(userId, product.getId(), key.getId(), payment.getId(), today, benefit.periodMonths()));
        payment.setSubscriptionId(subscription.getId());

        renewalWorker.grantMonthly(subscription, benefit, YearMonth.from(today));
        log.info("구독 등록: userId={}, subscriptionId={}, paymentId={}", userId, subscription.getId(), payment.getId());
        return toResponse(subscription, product, key, benefit);
    }

    /**
     * 내 구독 상태 (S8-19).
     *
     * @param userId 사용자
     * @return 상태
     */
    public SubscriptionStatusResponse getMy(Long userId) {
        Subscription subscription = subscriptionRepository
                .findFirstByUserIdAndSubStatusInAndDeletedOrderByIdDesc(userId, OPEN_STATUSES, YesNo.N)
                .or(() -> subscriptionRepository.findFirstByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N))
                .orElse(null);
        if (subscription == null) {
            return new SubscriptionStatusResponse(false, null);
        }
        Product product = productRepository.findById(subscription.getProductId()).orElseThrow();
        BillingKey key = billingKeyRepository.findById(subscription.getBillingKeyId()).orElse(null);
        return new SubscriptionStatusResponse(subscription.isEntitled(LocalDate.now()),
                toResponse(subscription, product, key, productBenefits.parse(product)));
    }

    /**
     * 혜택을 받는 중인지 확인한다(정밀매칭·24시간 스킵 등 다른 도메인이 쓴다).
     *
     * @param userId 사용자
     * @return 이용 중이면 {@code true}
     */
    public boolean isSubscribed(Long userId) {
        return subscriptionRepository
                .findFirstByUserIdAndSubStatusInAndDeletedOrderByIdDesc(userId, OPEN_STATUSES, YesNo.N)
                .map(s -> s.isEntitled(LocalDate.now()))
                .orElse(false);
    }

    /**
     * 구독을 해지한다. 남은 기간은 유지되고 다음 청구가 없다. 이미 해지했으면 그대로 돌려준다.
     *
     * @param userId 사용자
     * @return 구독 상세
     */
    @Transactional
    public SubscriptionResponse cancel(Long userId) {
        Subscription subscription = subscriptionRepository
                .findFirstByUserIdAndSubStatusInAndDeletedOrderByIdDesc(userId, OPEN_STATUSES, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (!Subscription.STATUS_CANCELED.equals(subscription.getSubStatus())) {
            subscription.cancel();
            log.info("구독 해지: userId={}, subscriptionId={}, 이용 종료일={}",
                    userId, subscription.getId(), subscription.getCurrentPeriodEnd());
        }
        Product product = productRepository.findById(subscription.getProductId()).orElseThrow();
        BillingKey key = billingKeyRepository.findById(subscription.getBillingKeyId()).orElse(null);
        return toResponse(subscription, product, key, productBenefits.parse(product));
    }

    /**
     * 청구일이 지난 구독을 갱신하고, 해지 후 기간이 끝난 구독을 종료한다. 건별로 독립 트랜잭션이라
     * 한 건이 실패해도 나머지는 진행된다.
     *
     * @param asOf 기준일
     * @return 실행 결과
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RenewResult renewDue(LocalDate asOf) {
        List<Long> dueIds = subscriptionRepository.findDueForBilling(asOf).stream().map(Subscription::getId).toList();
        int renewed = 0;
        int failed = 0;
        int expired = 0;
        for (Long id : dueIds) {
            try {
                switch (renewalWorker.renewOne(id, asOf)) {
                    case RENEWED -> renewed++;
                    case FAILED -> failed++;
                    case EXPIRED -> expired++;
                    default -> {
                    }
                }
            } catch (Exception e) {
                log.error("구독 갱신 처리 오류: subscriptionId={}", id, e);
                failed++;
            }
        }
        List<Long> endedIds = subscriptionRepository.findCanceledAndEnded(asOf).stream().map(Subscription::getId).toList();
        for (Long id : endedIds) {
            try {
                if (renewalWorker.expireOne(id)) {
                    expired++;
                }
            } catch (Exception e) {
                log.error("구독 종료 처리 오류: subscriptionId={}", id, e);
            }
        }
        log.info("구독 갱신 배치: asOf={}, due={}, renewed={}, failed={}, expired={}",
                asOf, dueIds.size(), renewed, failed, expired);
        return new RenewResult(asOf, dueIds.size(), renewed, failed, expired);
    }

    private BillingKey resolveBillingKey(Long userId, SubscriptionRequest request) {
        boolean hasAuthKey = request.authKey() != null && !request.authKey().isBlank();
        if (hasAuthKey || request.card() != null) {
            String customerKey = UUID.randomUUID().toString();
            PaymentGateway.IssuedBillingKey issued;
            try {
                issued = hasAuthKey
                        ? paymentGateway.issueBillingKey(customerKey, request.authKey())
                        : paymentGateway.issueBillingKeyByCard(customerKey, new PaymentGateway.CardInfo(
                        request.card().cardNumber(), request.card().expiryYear(), request.card().expiryMonth(),
                        request.card().identityNumber(), request.card().password()));
            } catch (GatewayException e) {
                log.warn("빌링키 발급 실패: userId={}, code={}", userId, e.getCode());
                throw new BusinessException(ErrorCode.PAYMENT_FAILED, "결제 수단을 등록하지 못했어요. (" + e.getCode() + ")");
            }
            return paymentService.storeBillingKey(userId, issued);
        }
        return billingKeyRepository.findByUserId(userId)
                .filter(BillingKey::isUsable)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_METHOD_REQUIRED));
    }

    private SubscriptionResponse toResponse(Subscription subscription, Product product, BillingKey key,
                                            ProductBenefits.Benefit benefit) {
        return SubscriptionResponse.of(subscription, product, key, benefit.monthlyGrants(), benefit.carryOverMonths());
    }
}
