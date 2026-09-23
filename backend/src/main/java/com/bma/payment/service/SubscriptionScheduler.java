package com.bma.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 매일 청구일이 지난 구독을 갱신한다. 토스는 자체 정기 청구 스케줄을 제공하지 않으므로 서버가 돌린다.
 *
 * <p>실행 시각은 {@code app.payment.subscription.renew-cron}. 수동 실행은
 * {@code POST /api/v1/admin/payments/subscriptions/renew}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${app.payment.subscription.renew-cron}")
    public void renewDaily() {
        log.info("구독 갱신 배치 시작");
        subscriptionService.renewDue(LocalDate.now());
    }
}
