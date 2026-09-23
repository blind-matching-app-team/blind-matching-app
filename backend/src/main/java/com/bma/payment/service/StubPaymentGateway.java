package com.bma.payment.service;

import com.bma.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 실제 PG 없이 항상 성공하는 게이트웨이. {@code app.payment.gateway=stub}(기본값)일 때 등록된다.
 *
 * <p>카드번호가 {@code 0000} 으로 시작하면 발급을 거부하고, 결제 키가 {@code fail-} 로 시작하면 승인을
 * 거부한다. 실패 경로를 통합 테스트에서 재현하기 위한 규칙이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "stub", matchIfMissing = true)
public class StubPaymentGateway implements PaymentGateway {

    private final AppProperties properties;

    @Override
    public Approval approve(String orderId, String paymentKey, BigDecimal amount) {
        log.warn("스텁 결제 게이트웨이로 승인 처리합니다. 운영 환경에서는 실제 PG 연동이 필요합니다. "
                + "orderId={}, amount={}", orderId, amount);
        if (paymentKey != null && paymentKey.startsWith("fail-")) {
            throw new GatewayException("STUB_REJECTED", "스텁 규칙에 따라 거부된 결제 키입니다.");
        }
        // 요청 금액을 그대로 승인한 것처럼 응답한다. 실제 PG는 자체 승인 금액을 돌려주며,
        // 서비스 계층은 그 값을 상품 가격과 대조한다.
        return new Approval(paymentKey, "DONE", amount,
                "{\"stub\":true,\"orderId\":\"" + orderId + "\"}");
    }

    @Override
    public IssuedBillingKey issueBillingKey(String customerKey, String authKey) {
        return new IssuedBillingKey("stub_bk_" + UUID.randomUUID(), customerKey, "스텁카드", "****-****-****-0000",
                "{\"stub\":true,\"authKey\":\"" + authKey + "\"}");
    }

    @Override
    public IssuedBillingKey issueBillingKeyByCard(String customerKey, CardInfo card) {
        if (card.cardNumber() != null && card.cardNumber().startsWith("0000")) {
            throw new GatewayException("INVALID_CARD_NUMBER", "스텁 규칙에 따라 거부된 카드번호입니다.");
        }
        String number = card.cardNumber() == null ? "" : card.cardNumber();
        String last4 = number.length() >= 4 ? number.substring(number.length() - 4) : "0000";
        return new IssuedBillingKey("stub_bk_" + UUID.randomUUID(), customerKey, "스텁카드",
                "****-****-****-" + last4, "{\"stub\":true}");
    }

    @Override
    public Approval chargeBillingKey(String billingKey, String customerKey, String orderId, String orderName,
                                     BigDecimal amount) {
        log.warn("스텁 게이트웨이로 자동결제 승인: orderId={}, amount={}", orderId, amount);
        return new Approval("stub_pk_" + UUID.randomUUID(), "DONE", amount,
                "{\"stub\":true,\"orderId\":\"" + orderId + "\",\"orderName\":\"" + orderName + "\"}");
    }

    @Override
    public Approval cancel(String paymentKey, String reason) {
        return new Approval(paymentKey, "CANCELED", null, "{\"stub\":true,\"cancelReason\":\"" + reason + "\"}");
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return WebhookSignatures.verify(properties.payment().webhookSecret(), rawBody, signature);
    }
}
