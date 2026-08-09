package com.bma.payment.service;

import com.bma.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 개발/테스트용 결제 게이트웨이 스텁.
 *
 * <p>고친 점: 기존 구현은 {@code @Primary}가 붙어 있어, 나중에 실제 TossPayments 구현을
 * 추가하더라도 <b>스텁이 계속 우선 선택</b>되는 위험한 상태였다. 운영에서 무조건
 * 승인 성공을 반환할 수 있다는 뜻이다.</p>
 *
 * <p>이제 {@code app.payment.gateway=stub}일 때만 빈으로 등록된다.
 * 실제 게이트웨이를 붙일 때는 설정 값을 바꾸고 새 구현체에
 * {@code @ConditionalOnProperty(name = "app.payment.gateway", havingValue = "toss")}를 붙이면 된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "stub", matchIfMissing = true)
public class StubPaymentGateway implements PaymentGateway {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AppProperties properties;

    @Override
    public Approval approve(String orderId, String paymentKey, BigDecimal amount) {
        log.warn("스텁 결제 게이트웨이로 승인 처리합니다. 운영 환경에서는 실제 PG 연동이 필요합니다. "
                + "orderId={}, amount={}", orderId, amount);
        // 요청 금액을 그대로 승인한 것처럼 응답한다. 실제 PG는 자체 승인 금액을 돌려주며,
        // 서비스 계층은 그 값을 상품 가격과 대조한다.
        return new Approval(paymentKey, "DONE", amount,
                "{\"stub\":true,\"orderId\":\"" + orderId + "\"}");
    }

    /**
     * {@inheritDoc}
     *
     * <p>시크릿이 설정되지 않은 개발 환경에서는 검증을 통과시키되 경고를 남긴다.
     * 운영에서는 {@code app.payment.webhook-secret}을 반드시 설정해야 한다.</p>
     */
    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        String secret = properties.payment().webhookSecret();

        if (secret == null || secret.isBlank()) {
            log.warn("웹훅 시크릿이 설정되지 않아 서명 검증을 건너뜁니다. "
                    + "운영 배포 전 app.payment.webhook-secret 을 설정하세요.");
            return true;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = Base64.getEncoder()
                    .encodeToString(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            // 타이밍 공격을 피하기 위해 상수 시간 비교를 사용한다.
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("웹훅 서명 검증 중 오류", e);
            return false;
        }
    }
}
