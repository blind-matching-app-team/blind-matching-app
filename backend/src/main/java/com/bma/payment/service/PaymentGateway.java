package com.bma.payment.service;

import java.math.BigDecimal;

/**
 * 결제 게이트웨이 추상화. 구현체는 {@code app.payment.gateway} 로 고른다(stub/toss).
 *
 * <p>모든 메서드는 실패 시 {@link GatewayException} 을 던진다. 서비스 계층은 원인 코드를 결제 원장에 남기고
 * 사용자에게는 {@code PAY_002} 로 통일해 응답한다.</p>
 */
public interface PaymentGateway {

    /**
     * 승인 결과.
     *
     * @param paymentKey  PG 결제 키
     * @param status      PG 상태(DONE/CANCELED/...)
     * @param amount      PG 가 실제 승인한 금액
     * @param rawResponse 원문
     */
    record Approval(String paymentKey, String status, BigDecimal amount, String rawResponse) {

        public boolean isDone() {
            return "DONE".equals(status);
        }
    }

    /**
     * 발급된 빌링키.
     *
     * @param billingKey       빌링키 원문(저장 전 반드시 암호화)
     * @param customerKey      쌍이 되는 customerKey
     * @param cardCompany      카드사
     * @param cardNumberMasked 마스킹 카드번호
     * @param rawResponse      원문
     */
    record IssuedBillingKey(String billingKey, String customerKey, String cardCompany,
                            String cardNumberMasked, String rawResponse) {
    }

    /**
     * 카드 직접 입력 정보(테스트 환경·API 개별 연동용).
     *
     * @param cardNumber     카드번호
     * @param expiryYear     유효기간 연도(2자리)
     * @param expiryMonth    유효기간 월(2자리)
     * @param identityNumber 생년월일 6자리 또는 사업자번호 10자리
     * @param password       비밀번호 앞 2자리
     */
    record CardInfo(String cardNumber, String expiryYear, String expiryMonth,
                    String identityNumber, String password) {
    }

    /**
     * 결제창에서 받은 결제를 승인한다.
     *
     * @param orderId    주문 ID
     * @param paymentKey 결제창이 발급한 결제 키
     * @param amount     서버가 기대하는 금액
     * @return 승인 결과
     */
    Approval approve(String orderId, String paymentKey, BigDecimal amount);

    /**
     * 결제창(빌링 위젯)에서 받은 authKey 로 빌링키를 발급한다.
     *
     * @param customerKey 사용자 식별 키(UUID 권장)
     * @param authKey     결제창이 준 authKey
     * @return 빌링키
     */
    IssuedBillingKey issueBillingKey(String customerKey, String authKey);

    /**
     * 카드 정보로 직접 빌링키를 발급한다.
     *
     * @param customerKey 사용자 식별 키
     * @param card        카드 정보
     * @return 빌링키
     */
    IssuedBillingKey issueBillingKeyByCard(String customerKey, CardInfo card);

    /**
     * 빌링키로 자동결제를 승인한다.
     *
     * @param billingKey  빌링키
     * @param customerKey 쌍이 되는 customerKey
     * @param orderId     주문 ID
     * @param orderName   주문명
     * @param amount      금액
     * @return 승인 결과
     */
    Approval chargeBillingKey(String billingKey, String customerKey, String orderId, String orderName,
                              BigDecimal amount);

    /**
     * 결제를 취소한다(전액).
     *
     * @param paymentKey 결제 키
     * @param reason     취소 사유
     * @return 취소 후 상태
     */
    Approval cancel(String paymentKey, String reason);

    /**
     * 웹훅 서명을 검증한다.
     *
     * @param rawBody   원문
     * @param signature 서명 헤더
     * @return 유효하면 {@code true}
     */
    boolean verifyWebhookSignature(String rawBody, String signature);
}
