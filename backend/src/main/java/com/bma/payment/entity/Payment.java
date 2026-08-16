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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 원장({@code PY_PAYMENT}).
 *
 * <p>{@code UK_PY_PAYMENT_ORDER(ORDER_ID)} 제약이 있어 같은 주문 ID로 두 번 생성할 수 없다.
 * 이 제약을 멱등성 키로 활용한다.</p>
 */
@Entity
@Table(name = "PY_PAYMENT")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseAuditEntity {

    /** 결제 요청 생성됨(승인 전). */
    public static final String STATUS_READY = "READY";

    /** 승인 완료. */
    public static final String STATUS_DONE = "DONE";

    /** 취소됨. */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 승인 실패. */
    public static final String STATUS_FAILED = "FAILED";

    /** 내부 결제 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PAYMENT_ID")
    private Long id;

    /** 결제자 ID. */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 구매 상품 ID. */
    @Column(name = "PRODUCT_ID", nullable = false)
    private Long productId;

    /** PG 주문 ID. 멱등성 키로 사용한다. */
    @Column(name = "ORDER_ID", nullable = false, length = 100)
    private String orderId;

    /** PG 결제 키. */
    @Column(name = "PAYMENT_KEY", length = 200)
    private String paymentKey;

    /** 결제 상태. */
    @Column(name = "PAYMENT_STATUS", nullable = false, length = 30)
    private String paymentStatus = STATUS_READY;

    /** 결제 금액. 상품 가격에서 서버가 결정한다. */
    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    /** 통화 코드. */
    @Column(name = "CURRENCY_CODE", nullable = false, length = 3)
    private String currencyCode = "KRW";

    /** 승인 일시. */
    @Column(name = "APPROVED_DATE")
    private LocalDateTime approvedDate;

    /** 취소 일시. */
    @Column(name = "CANCELED_DATE")
    private LocalDateTime canceledDate;

    /** 실패 코드. */
    @Column(name = "FAIL_CODE", length = 100)
    private String failCode;

    /** 실패 메시지. */
    @Column(name = "FAIL_MESSAGE", length = 1000)
    private String failMessage;

    /**
     * 승인 전 결제 요청을 만든다.
     *
     * @param userId    결제자
     * @param productId 상품 ID
     * @param orderId   주문 ID
     * @param amount    금액(상품 가격)
     * @param currency  통화
     * @return 저장 대상 엔티티
     */
    public static Payment ready(Long userId, Long productId, String orderId,
                                BigDecimal amount, String currency) {
        Payment payment = new Payment();
        payment.userId = userId;
        payment.productId = productId;
        payment.orderId = orderId;
        payment.amount = amount;
        payment.currencyCode = currency;
        payment.paymentStatus = STATUS_READY;
        return payment;
    }

    /**
     * 승인 완료로 전이한다.
     *
     * @param paymentKey PG 결제 키
     */
    public void approve(String paymentKey) {
        this.paymentKey = paymentKey;
        this.paymentStatus = STATUS_DONE;
        this.approvedDate = LocalDateTime.now();
        this.failCode = null;
        this.failMessage = null;
    }

    /**
     * 실패로 전이한다.
     *
     * @param code    실패 코드
     * @param message 실패 메시지
     */
    public void fail(String code, String message) {
        this.paymentStatus = STATUS_FAILED;
        this.failCode = code;
        this.failMessage = truncate(message);
    }

    /**
     * 취소로 전이한다.
     */
    public void cancel() {
        this.paymentStatus = STATUS_CANCELED;
        this.canceledDate = LocalDateTime.now();
    }

    /**
     * 이미 승인이 끝난 결제인지 확인한다.
     *
     * @return 승인 완료 상태이면 {@code true}
     */
    public boolean isApproved() {
        return STATUS_DONE.equals(paymentStatus);
    }

    /**
     * 실패 메시지를 컬럼 길이에 맞게 자른다.
     *
     * @param message 원본 메시지
     * @return 잘린 메시지
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
