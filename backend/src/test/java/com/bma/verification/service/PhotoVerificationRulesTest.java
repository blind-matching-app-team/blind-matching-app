package com.bma.verification.service;

import com.bma.verification.service.FaceComparisonGateway.FaceMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BMA-82 사진인증 규칙(스텁 대조, 파일 시그니처 판별)을 고정한다.
 */
class PhotoVerificationRulesTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0};

    @Test
    @DisplayName("스텁: 같은 파일이면 유사도 100, 다른 파일이면 0, 빈 파일은 얼굴 없음")
    void stubCompare() {
        StubFaceComparisonGateway gw = new StubFaceComparisonGateway();
        FaceMatch same = gw.compare(PNG.clone(), "image/png", PNG.clone(), "image/png");
        assertThat(same.faceDetected()).isTrue();
        assertThat(same.similarity()).isEqualTo(100);

        FaceMatch different = gw.compare(JPEG, "image/jpeg", PNG, "image/png");
        assertThat(different.faceDetected()).isTrue();
        assertThat(different.similarity()).isZero();

        assertThat(gw.compare(new byte[0], "image/png", PNG, "image/png").faceDetected()).isFalse();
        assertThat(gw.provider()).isEqualTo("stub");
    }

    @Test
    @DisplayName("파일 시그니처로 형식을 판별한다 — Content-Type 헤더는 믿지 않는다")
    void detectImageType() {
        assertThat(PhotoVerificationService.ImageBytes.detect(PNG)).isEqualTo("image/png");
        assertThat(PhotoVerificationService.ImageBytes.detect(JPEG)).isEqualTo("image/jpeg");
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertThat(PhotoVerificationService.ImageBytes.detect(webp)).isEqualTo("image/webp");
        byte[] heic = "\0\0\0\u0018ftypheic\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(PhotoVerificationService.ImageBytes.detect(heic)).isEqualTo("image/heic");
        assertThat(PhotoVerificationService.ImageBytes.detect("not an image at all".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(PhotoVerificationService.ImageBytes.detect(new byte[3])).isNull();
    }
}
