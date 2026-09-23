package com.bma.verification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * 인증사 스텁 (업체 확정 전 개발·검증용, {@code app.verification.identity.provider=stub}, 기본값).
 *
 * <p>프론트가 SDK 대신 {@code result} 객체에 이름·생년월일·성별·전화번호를 담아 {@code confirm} 으로 보내면 그대로 신뢰한다.
 * CI 는 이름+생년월일+전화번호의 SHA-256 으로 만들어 같은 사람이면 같은 값이 나오게 한다(중복 계정 검증 재현).
 * 운영에서는 절대 쓰면 안 되며, 실제 업체 구현체가 들어오면 설정만 바꾼다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.verification.identity.provider", havingValue = "stub", matchIfMissing = true)
public class StubIdentityVerificationGateway implements IdentityVerificationGateway {

    public static final String PROVIDER = "stub";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Map<String, Object> begin(String transactionId, Long userId) {
        // 실제 업체라면 여기서 업체 API 로 인증창 토큰을 받는다. 스텁은 형식만 흉내 낸다.
        return Map.of(
                "sdkToken", "stub-" + UUID.randomUUID(),
                "sdkUrl", "stub://identity/" + transactionId,
                "note", "스텁: confirm 의 result 에 name/birthDate/genderCode/phoneNumber 를 넣으면 그대로 인증된다");
    }

    @Override
    @SuppressWarnings("unchecked")
    public IdentityResult confirm(String transactionId, Map<String, Object> providerPayload) {
        Object raw = providerPayload == null ? null : providerPayload.get("result");
        if (!(raw instanceof Map<?, ?> result)) {
            throw new IdentityVerificationException("스텁 결과(result)가 없습니다.");
        }
        Map<String, Object> r = (Map<String, Object>) result;
        String name = text(r.get("name"));
        String birth = text(r.get("birthDate"));
        String gender = text(r.get("genderCode"));
        String phone = text(r.get("phoneNumber"));
        if (name == null || birth == null) {
            throw new IdentityVerificationException("스텁 결과에는 name 과 birthDate 가 필요합니다.");
        }
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(birth);
        } catch (DateTimeParseException e) {
            throw new IdentityVerificationException("birthDate 형식은 yyyy-MM-dd 여야 합니다.");
        }
        String ci = text(r.get("ci"));
        if (ci == null) {
            ci = sha256(name + "|" + birth + "|" + (phone == null ? "" : phone));
        }
        log.info("스텁 본인인증 결과 수신: tx={}, birthDate={}", transactionId, birthDate);
        return new IdentityResult(name, birthDate, gender, phone, ci);
    }

    private static String text(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
