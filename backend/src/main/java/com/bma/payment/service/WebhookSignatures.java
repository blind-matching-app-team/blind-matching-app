package com.bma.payment.service;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 웹훅 HMAC-SHA256 서명 검증. 스텁/토스 게이트웨이가 공유한다.
 */
@Slf4j
final class WebhookSignatures {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private WebhookSignatures() {
    }

    /**
     * 서명을 검증한다.
     *
     * @param secret    공유 시크릿. 비어 있으면 검증을 건너뛴다(경고)
     * @param rawBody   원문
     * @param signature 서명(Base64)
     * @return 유효하면 {@code true}
     */
    static boolean verify(String secret, String rawBody, String signature) {
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
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("웹훅 서명 검증 중 오류", e);
            return false;
        }
    }
}
