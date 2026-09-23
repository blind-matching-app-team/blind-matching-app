package com.bma.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구독 기간·청구일·해지·유예 규칙을 고정한다.
 */
class SubscriptionTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 31);

    @Test
    @DisplayName("시작하면 기간은 [시작일, 시작일+1개월-1일], 다음 청구일은 기간 종료 다음 날")
    void startPeriod() {
        Subscription s = Subscription.start(1L, 10L, 100L, 1000L, START, 1);

        assertThat(s.getSubStatus()).isEqualTo(Subscription.STATUS_ACTIVE);
        assertThat(s.getCurrentPeriodStart()).isEqualTo(START);
        assertThat(s.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 27)); // 1/31 + 1개월 = 2/28, -1일
        assertThat(s.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(s.isEntitled(LocalDate.of(2026, 2, 27))).isTrue();
        assertThat(s.isEntitled(LocalDate.of(2026, 2, 28))).isFalse();
    }

    @Test
    @DisplayName("갱신하면 직전 기간 종료 다음 날부터 새 기간을 세고 실패 횟수는 초기화된다")
    void renewContinuesFromPeriodEnd() {
        Subscription s = Subscription.start(1L, 10L, 100L, 1000L, START, 1);
        s.recordFailure(3);
        assertThat(s.getSubStatus()).isEqualTo(Subscription.STATUS_PAST_DUE);

        s.renew(1001L, 1);

        assertThat(s.getSubStatus()).isEqualTo(Subscription.STATUS_ACTIVE);
        assertThat(s.getFailCount()).isZero();
        assertThat(s.getLastPaymentId()).isEqualTo(1001L);
        assertThat(s.getCurrentPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(s.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 27));
        assertThat(s.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 3, 28));
    }

    @Test
    @DisplayName("유예 일수를 넘는 연속 실패는 EXPIRED 로 끝난다")
    void failuresBeyondGraceExpire() {
        Subscription s = Subscription.start(1L, 10L, 100L, 1000L, START, 1);

        assertThat(s.recordFailure(2)).isFalse(); // 1
        assertThat(s.recordFailure(2)).isFalse(); // 2
        assertThat(s.recordFailure(2)).isTrue();  // 3 > 2

        assertThat(s.getSubStatus()).isEqualTo(Subscription.STATUS_EXPIRED);
        assertThat(s.getNextBillingDate()).isNull();
        assertThat(s.getEndedDate()).isNotNull();
        assertThat(s.isOpen()).isFalse();
        assertThat(s.isBillable()).isFalse();
    }

    @Test
    @DisplayName("해지하면 다음 청구는 없어지지만 기간 종료일까지는 혜택이 유지된다")
    void cancelKeepsEntitlementUntilPeriodEnd() {
        Subscription s = Subscription.start(1L, 10L, 100L, 1000L, START, 1);
        s.cancel();

        assertThat(s.getSubStatus()).isEqualTo(Subscription.STATUS_CANCELED);
        assertThat(s.getNextBillingDate()).isNull();
        assertThat(s.getCanceledDate()).isNotNull();
        assertThat(s.isBillable()).isFalse();
        assertThat(s.isOpen()).isTrue();
        assertThat(s.isEntitled(LocalDate.of(2026, 2, 27))).isTrue();
        assertThat(s.isEntitled(LocalDate.of(2026, 2, 28))).isFalse();
    }
}
