package com.bma.payment.service;

import com.bma.common.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 빌링키 AES-256-GCM 암복호화. 암호문 앞 12바이트가 nonce 다.
 *
 * <p>빌링키는 customerKey 와 함께 있으면 그 자체로 결제 수단이라 평문 보관을 피한다. 키는
 * {@code app.payment.billing-key-secret} 에서 SHA-256 으로 파생하고, 비어 있으면 JWT 시크릿을 대신 쓰되
 * 경고를 남긴다(운영에서는 별도 시크릿을 설정한다).</p>
 */
@Slf4j
@Component
public class BillingKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    // 생성자가 둘(설정용·테스트용)이라 스프링이 고를 수 있게 명시한다.
    @Autowired
    public BillingKeyCipher(AppProperties properties) {
        this(resolveSecret(properties));
    }

    BillingKeyCipher(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("빌링키 암호화 키를 만들 수 없습니다.", e);
        }
    }

    private static String resolveSecret(AppProperties properties) {
        String secret = properties.payment().billingKeySecret();
        if (secret != null && !secret.isBlank()) {
            return secret;
        }
        log.warn("app.payment.billing-key-secret 이 비어 있어 JWT 시크릿으로 빌링키를 암호화합니다. "
                + "운영 배포 전 BILLING_KEY_SECRET 을 따로 설정하세요.");
        return properties.jwt().secret();
    }

    /**
     * 암호화한다.
     *
     * @param plain 빌링키 원문
     * @return nonce + 암호문
     */
    public byte[] encrypt(String plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[NONCE_BYTES + encrypted.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(encrypted, 0, out, NONCE_BYTES, encrypted.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("빌링키 암호화에 실패했습니다.", e);
        }
    }

    /**
     * 복호화한다.
     *
     * @param data nonce + 암호문
     * @return 빌링키 원문
     */
    public String decrypt(byte[] data) {
        try {
            byte[] nonce = Arrays.copyOfRange(data, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(data, NONCE_BYTES, data.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("빌링키 복호화에 실패했습니다. 암호화 키가 바뀌었을 수 있습니다.", e);
        }
    }
}
