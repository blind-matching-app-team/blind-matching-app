package com.bma.payment.service;

import java.math.BigDecimal;

/**
 * 결제 게이트웨이 추상화(TossPayments 등).
 */
public interface PaymentGateway {

    /**
     * 승인 결과.
     *
     * @param paymentKey PG 결제 키
     * @param status     PG가 반환한 상태(DONE/CANCELED 등)
     * @param amount     PG가 실제로 승인한 금액. 서버가 기대한 금액과 반드시 대조해야 한다.
     * @param rawResponse 원문 응답. 감사 로그로 남긴다.
     */
    record Approval(String paymentKey, String status, BigDecimal amount, String rawResponse) {

        /**
         * 승인 성공 여부.
         *
         * @return 상태가 DONE이면 {@code true}
         */
        public boolean isDone() {
            return "DONE".equals(status);
        }
    }

    /**
     * 결제를 승인한다.
     *
     * @param orderId    주문 ID
     * @param paymentKey PG 결제 키
     * @param amount     서버가 결정한 승인 요청 금액
     * @return 승인 결과
     */
    Approval approve(String orderId, String paymentKey, BigDecimal amount);

    /**
     * 웹훅 서명을 검증한다.
     *
     * @param rawBody   웹훅 본문 원문
     * @param signature 요청 헤더의 서명 값
     * @return 유효한 서명이면 {@code true}
     */
    boolean verifyWebhookSignature(String rawBody, String signature);
}
