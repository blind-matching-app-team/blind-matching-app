package com.bma.support;

import com.bma.common.config.AppProperties;

import java.util.List;

/**
 * 테스트에서 {@link AppProperties}를 손쉽게 만들기 위한 헬퍼.
 *
 * <p>레코드라 생성자 인자가 많으므로, 기본값을 모아 두고 필요한 값만 바꿔 쓴다.</p>
 */
public final class TestProperties {

    /** 테스트용 JWT 시크릿(32바이트 이상). */
    public static final String TEST_SECRET = "test-secret-key-for-unit-tests-0123456789";

    private TestProperties() {
    }

    /**
     * 기본 설정을 만든다.
     *
     * @return 테스트용 설정
     */
    public static AppProperties defaults() {
        return withStorage(System.getProperty("java.io.tmpdir"), 10 * 1024 * 1024L, 6);
    }

    /**
     * 저장소 설정만 지정한 설정을 만든다.
     *
     * @param storageRoot      저장 루트
     * @param maxFileSizeBytes 최대 파일 크기
     * @param maxImages        사용자당 이미지 수
     * @return 테스트용 설정
     */
    public static AppProperties withStorage(String storageRoot, long maxFileSizeBytes, int maxImages) {
        return new AppProperties(
                new AppProperties.Jwt(TEST_SECRET, 1800, 1_209_600),
                new AppProperties.Storage(storageRoot, maxFileSizeBytes,
                        List.of("image/jpeg", "image/png", "image/webp"), maxImages),
                new AppProperties.Cors(List.of("http://localhost:3000"), List.of("GET", "POST"), true),
                new AppProperties.Websocket(List.of("http://localhost:3000")),
                new AppProperties.Payment("stub", ""),
                new AppProperties.Matching(50, 30));
    }
}
