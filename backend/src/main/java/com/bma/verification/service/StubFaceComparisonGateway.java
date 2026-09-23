package com.bma.verification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 얼굴 대조 스텁 ({@code app.verification.photo.provider=stub}, 기본값).
 *
 * <p>얼굴 모델 없이 흐름만 검증한다: 셀피가 프로필 사진과 <b>같은 파일</b>이면 유사도 100, 아니면 0.
 * 실제 제공자(AWS Rekognition CompareFaces 등)가 들어오면 설정만 바꾼다. 운영 금지.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.verification.photo.provider", havingValue = "stub", matchIfMissing = true)
public class StubFaceComparisonGateway implements FaceComparisonGateway {

    public static final String PROVIDER = "stub";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public FaceMatch compare(byte[] selfie, String selfieContentType, byte[] reference, String referenceContentType) {
        if (selfie == null || selfie.length == 0 || reference == null || reference.length == 0) {
            return new FaceMatch(false, 0);
        }
        boolean same = Arrays.equals(digest(selfie), digest(reference));
        log.info("스텁 얼굴 대조: selfie={}B, reference={}B, same={}", selfie.length, reference.length, same);
        return new FaceMatch(true, same ? 100 : 0);
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
