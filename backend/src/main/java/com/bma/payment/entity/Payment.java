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
 * 결제 원장({@code PY_PAYMENT}). 결제창 승인(소모형)과 빌링키 자동결제(소모형 저장카드·구독 청구)를 모두 기록한다.
 */
@Entity
@Table(name = "PY_PAYMENT")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseAuditEntity {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_FAILED = "FAILED";

    /** 소모형 이용권 단건 결제. */
    public static final String KIND_CONSUMABLE = "CONSUMABLE";

    /** 구독 청구(첫 결제 포함). */
    public static final String KIND_SUBSCRIPTION = "SUBSCRIPTION";

    /** 결제창(위젯)에서 받은 paymentKey 를 서버가 승인. */
    public static final String METHOD_WIDGET = "WIDGET";

    /** 저장된 빌링키로 서버가 직접 청구. */
    public static final String METHOD_BILLING = "BILLING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PAYMENT_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "PRODUCT_ID", nullable = false)
    private Long productId;

    @Column(name = "PAYMENT_KIND", nullable = false, length = 20)
    private String paymentKind = KIND_CONSUMABLE;

    @Column(name = "PAY_METHOD", length = 20)
    private String payMethod;

    @Column(name = "ORDER_ID", nullable = false, length = 100)
    private String orderId;

    @Column(name = "ORDER_NAME", length = 200)
    private String orderName;

    @Column(name = "PAYMENT_KEY", length = 200)
    private String paymentKey;

    @Column(name = "SUBSCRIPTION_ID")
    private Long subscriptionId;

    @Column(name = "PAYMENT_STATUS", nullable = false, length = 30)
    private String paymentStatus = STATUS_READY;

    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    @Column(name = "CURRENCY_CODE", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode = "KRW";

    @Column(name = "APPROVED_DATE")
    private LocalDateTime approvedDate;

    @Column(name = "CANCELED_DATE")
    private LocalDateTime canceledDate;

    @Column(name = "FAIL_CODE", length = 100)
    private String failCode;

    @Column(name = "FAIL_MESSAGE", length = 1000)
    private String failMessage;

    /**
     * 승인 대기 상태의 결제 행을 만든다.
     *
     * @param userId    사용자
     * @param product   상품
     * @param kind      결제 종류
     * @param method    결제 수단
     * @param orderId   주문 ID
     * @param orderName 주문명
     * @return 엔티티
     */
    public static Payment ready(Long userId, Product product, String kind, String method,
                                String orderId, String orderName) {
        Payment payment = new Payment();
        payment.userId = userId;
        payment.productId = product.getId();
        payment.paymentKind = kind;
        payment.payMethod = method;
        payment.orderId = orderId;
        payment.orderName = orderName;
        payment.amount = product.getPrice();
        payment.currencyCode = product.getCurrencyCode();
        payment.paymentStatus = STATUS_READY;
        return payment;
    }

    public void approve(String paymentKey) {
        this.paymentKey = paymentKey;
        this.paymentStatus = STATUS_DONE;
        this.approvedDate = LocalDateTime.now();
        this.failCode = null;
        this.failMessage = null;
    }

    public void fail(String code, String message) {
        this.paymentStatus = STATUS_FAILED;
        this.failCode = code;
        this.failMessage = truncate(message);
    }

    public void cancel() {
        this.paymentStatus = STATUS_CANCELED;
        this.canceledDate = LocalDateTime.now();
    }

    public boolean isApproved() {
        return STATUS_DONE.equals(paymentStatus);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
