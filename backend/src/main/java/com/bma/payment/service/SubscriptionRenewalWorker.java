package com.bma.payment.service;

import com.bma.common.config.AppProperties;
import com.bma.common.exception.BusinessException;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.service.NotificationService;
import com.bma.payment.entity.ItemType;
import com.bma.payment.entity.Payment;
import com.bma.payment.entity.Product;
import com.bma.payment.entity.Subscription;
import com.bma.payment.repository.ProductRepository;
import com.bma.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * 구독 청구·종료·월 지급의 건별 트랜잭션 단위.
 *
 * <p>{@link SubscriptionService#renewDue} 가 건마다 이 빈을 호출한다. 같은 클래스 안에서 호출하면
 * 프록시를 거치지 않아 {@code REQUIRES_NEW} 가 적용되지 않으므로 별도 빈으로 뺐다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionRenewalWorker {

    /** 갱신 1건의 결과. */
    public enum Outcome { RENEWED, FAILED, EXPIRED, SKIPPED }

    private final SubscriptionRepository subscriptionRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final ProductBenefits productBenefits;
    private final ItemWalletService walletService;
    private final NotificationService notificationService;
    private final AppProperties properties;

    /**
     * 구독 1건을 청구한다. 독립 트랜잭션이라 다른 건의 실패에 영향받지 않는다.
     *
     * @param subscriptionId 구독
     * @param asOf           기준일
     * @return 결과
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome renewOne(Long subscriptionId, LocalDate asOf) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null || !subscription.isBillable() || subscription.getNextBillingDate() == null
                || subscription.getNextBillingDate().isAfter(asOf)) {
            return Outcome.SKIPPED;
        }
        Product product = productRepository.findById(subscription.getProductId()).orElseThrow();
        ProductBenefits.Benefit benefit = productBenefits.parse(product);

        Payment payment;
        try {
            payment = paymentService.chargeWithBillingKey(subscription.getUserId(), product,
                    Payment.KIND_SUBSCRIPTION, subscription.getId(), product.getProductName() + " (정기 청구)");
        } catch (BusinessException e) {
            boolean ended = subscription.recordFailure(properties.payment().subscription().graceDays());
            if (ended) {
                notificationService.notify(subscription.getUserId(), NotificationEvent.SYSTEM,
                        "구독이 종료됐어요", "정기 결제가 계속 실패해 정밀매칭 구독을 종료했어요. 다시 구독할 수 있어요.",
                        "SUBSCRIPTION", subscription.getId());
                log.warn("구독 종료(유예 초과): subscriptionId={}", subscription.getId());
                return Outcome.EXPIRED;
            }
            notificationService.notify(subscription.getUserId(), NotificationEvent.SYSTEM,
                    "구독 결제에 실패했어요", "등록된 카드로 결제하지 못했어요. 결제 수단을 확인해주세요.",
                    "SUBSCRIPTION", subscription.getId());
            log.warn("구독 청구 실패: subscriptionId={}, failCount={}", subscription.getId(), subscription.getFailCount());
            return Outcome.FAILED;
        }

        subscription.renew(payment.getId(), benefit.periodMonths());
        grantMonthly(subscription, benefit, YearMonth.from(subscription.getCurrentPeriodStart()));
        log.info("구독 갱신: subscriptionId={}, 기간={}~{}", subscription.getId(),
                subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
        return Outcome.RENEWED;
    }

    /**
     * 해지 후 기간이 끝난 구독을 종료한다. 독립 트랜잭션.
     *
     * @param subscriptionId 구독
     * @return 종료했으면 {@code true}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOne(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .filter(Subscription::isOpen)
                .map(s -> {
                    s.expire();
                    log.info("구독 이용 종료: subscriptionId={}", subscriptionId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 이번 달 이용권을 지급한다. 같은 구독·달·종류는 한 번만 지급된다.
     *
     * @param subscription 구독
     * @param benefit      상품 혜택
     * @param month        지급 대상 월
     */
    @Transactional
    public void grantMonthly(Subscription subscription, ProductBenefits.Benefit benefit, YearMonth month) {
        for (Map.Entry<String, Integer> grant : benefit.monthlyGrants().entrySet()) {
            ItemType type = ItemType.of(grant.getKey());
            if (type == null || grant.getValue() == null || grant.getValue() <= 0) {
                continue;
            }
            walletService.grantMonthly(subscription.getUserId(), subscription.getId(), month, type,
                    grant.getValue(), benefit.carryOverMonths());
        }
    }
}
