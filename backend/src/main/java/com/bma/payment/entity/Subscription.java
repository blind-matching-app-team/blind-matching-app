package com.bma.payment.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정밀매칭 구독({@code PY_SUBSCRIPTION}).
 *
 * <p>이용 기간은 [CURRENT_PERIOD_START, CURRENT_PERIOD_END] 이고, 종료일 다음 날이 다음 청구일이다.
 * 해지하면 다음 청구만 멈추고 남은 기간은 그대로 쓴다(환불 없음). 청구가 실패하면 {@code PAST_DUE}
 * 로 두고 유예 일수 동안 매일 재시도한 뒤 {@code EXPIRED} 로 끝낸다.</p>
 */
@Entity
@Table(name = "PY_SUBSCRIPTION")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseAuditEntity {

    /** 이용 중, 다음 청구 예정. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 청구 실패, 유예 기간 중 재시도. */
    public static final String STATUS_PAST_DUE = "PAST_DUE";

    /** 해지 요청됨. 기간 종료까지 이용 가능, 청구 없음. */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 이용 종료. */
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SUBSCRIPTION_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "PRODUCT_ID", nullable = false)
    private Long productId;

    @Column(name = "BILLING_KEY_ID", nullable = false)
    private Long billingKeyId;

    @Column(name = "SUB_STATUS", nullable = false, length = 20)
    private String subStatus = STATUS_ACTIVE;

    @Column(name = "STARTED_DATE", nullable = false)
    private LocalDateTime startedDate;

    @Column(name = "CURRENT_PERIOD_START", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "CURRENT_PERIOD_END", nullable = false)
    private LocalDate currentPeriodEnd;

    @Column(name = "NEXT_BILLING_DATE")
    private LocalDate nextBillingDate;

    @Column(name = "FAIL_COUNT", nullable = false)
    private int failCount;

    @Column(name = "LAST_PAYMENT_ID")
    private Long lastPaymentId;

    @Column(name = "CANCELED_DATE")
    private LocalDateTime canceledDate;

    @Column(name = "ENDED_DATE")
    private LocalDateTime endedDate;

    /**
     * 첫 결제가 끝난 직후 구독을 시작한다.
     *
     * @param userId       사용자
     * @param productId    구독 상품
     * @param billingKeyId 청구에 쓸 빌링키
     * @param paymentId    첫 결제
     * @param start        이용 시작일
     * @param periodMonths 한 주기의 개월 수
     * @return 구독
     */
    public static Subscription start(Long userId, Long productId, Long billingKeyId, Long paymentId,
                                     LocalDate start, int periodMonths) {
        Subscription subscription = new Subscription();
        subscription.userId = userId;
        subscription.productId = productId;
        subscription.billingKeyId = billingKeyId;
        subscription.startedDate = LocalDateTime.now();
        subscription.lastPaymentId = paymentId;
        subscription.subStatus = STATUS_ACTIVE;
        subscription.setPeriod(start, periodMonths);
        return subscription;
    }

    /**
     * 청구에 성공해 다음 주기로 넘어간다. 유예 중이던 실패 횟수는 초기화한다.
     *
     * @param paymentId    이번 결제
     * @param periodMonths 한 주기의 개월 수
     */
    public void renew(Long paymentId, int periodMonths) {
        this.lastPaymentId = paymentId;
        this.failCount = 0;
        this.subStatus = STATUS_ACTIVE;
        // 청구일(= 직전 기간 종료 다음 날)부터 새 기간을 센다. 유예 중 늦게 성공해도 기간이 밀리지 않는다.
        setPeriod(this.currentPeriodEnd.plusDays(1), periodMonths);
    }

    /**
     * 청구 실패를 기록한다.
     *
     * @param graceDays 유예 일수. 실패 횟수가 이를 넘으면 종료한다
     * @return 이번 실패로 종료됐으면 {@code true}
     */
    public boolean recordFailure(int graceDays) {
        this.failCount++;
        if (this.failCount > graceDays) {
            expire();
            return true;
        }
        this.subStatus = STATUS_PAST_DUE;
        return false;
    }

    /** 해지 요청. 남은 기간은 유지하고 다음 청구를 없앤다. */
    public void cancel() {
        this.subStatus = STATUS_CANCELED;
        this.canceledDate = LocalDateTime.now();
        this.nextBillingDate = null;
    }

    /** 이용을 끝낸다. */
    public void expire() {
        this.subStatus = STATUS_EXPIRED;
        this.nextBillingDate = null;
        this.endedDate = LocalDateTime.now();
    }

    /**
     * 혜택을 받을 수 있는 상태인지 확인한다. 해지했어도 기간이 남았으면 참이다.
     *
     * @param asOf 기준일
     * @return 이용 중이면 {@code true}
     */
    public boolean isEntitled(LocalDate asOf) {
        if (isDeleted() || STATUS_EXPIRED.equals(subStatus)) {
            return false;
        }
        return !asOf.isAfter(currentPeriodEnd);
    }

    /**
     * 살아 있는(종료되지 않은) 구독인지 확인한다.
     *
     * @return ACTIVE/PAST_DUE/CANCELED 이면 {@code true}
     */
    public boolean isOpen() {
        return !isDeleted() && !STATUS_EXPIRED.equals(subStatus);
    }

    /**
     * 청구가 예정된 상태인지 확인한다.
     *
     * @return ACTIVE/PAST_DUE 이면 {@code true}
     */
    public boolean isBillable() {
        return STATUS_ACTIVE.equals(subStatus) || STATUS_PAST_DUE.equals(subStatus);
    }

    private void setPeriod(LocalDate start, int periodMonths) {
        this.currentPeriodStart = start;
        this.currentPeriodEnd = start.plusMonths(periodMonths).minusDays(1);
        this.nextBillingDate = this.currentPeriodEnd.plusDays(1);
    }
}
