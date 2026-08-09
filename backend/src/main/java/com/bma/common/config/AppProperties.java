package com.bma.common.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * application.yml 의 {@code app.*} 설정을 타입 안전하게 바인딩하는 설정 홀더.
 *
 * <p>기존 코드는 {@code @Value}로 문자열을 여기저기서 직접 읽었기 때문에 오타가 나도 기동 시점에
 * 발견되지 않았다. 레코드 + 생성자 바인딩으로 바꾸면 필수 값 누락이 기동 시점에 바로 드러난다.</p>
 *
 * @param jwt       토큰 서명/만료 설정
 * @param storage   프로필 이미지 저장소 설정
 * @param cors      REST API CORS 허용 정책
 * @param websocket STOMP 핸드셰이크 허용 정책
 * @param payment   결제 게이트웨이 설정
 * @param matching  추천/매칭 관련 정책
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Jwt jwt,
        @NotNull Storage storage,
        @NotNull Cors cors,
        @NotNull Websocket websocket,
        @NotNull Payment payment,
        @NotNull Matching matching
) {

    /**
     * JWT 설정.
     *
     * @param secret               HS256 서명 키. 최소 32바이트여야 하며 운영 환경에서는 반드시 외부 주입한다.
     * @param accessTokenSeconds   액세스 토큰 유효 기간(초)
     * @param refreshTokenSeconds  리프레시 토큰 유효 기간(초)
     */
    public record Jwt(String secret, long accessTokenSeconds, long refreshTokenSeconds) {
    }

    /**
     * 파일 저장소 설정.
     *
     * @param root                저장 루트 디렉터리. 이 경로 밖으로는 절대 파일을 쓰지 않는다.
     * @param maxFileSizeBytes    업로드 1건당 최대 바이트 수
     * @param allowedContentTypes 허용 MIME 타입 화이트리스트
     * @param maxImagesPerUser    사용자당 보유 가능한 프로필 이미지 수
     */
    public record Storage(String root,
                          long maxFileSizeBytes,
                          List<String> allowedContentTypes,
                          int maxImagesPerUser) {
    }

    /**
     * CORS 설정.
     *
     * @param allowedOrigins   허용 Origin 목록. 와일드카드(*)는 자격 증명 전송과 함께 쓸 수 없으므로 명시적으로 나열한다.
     * @param allowedMethods   허용 HTTP 메서드
     * @param allowCredentials 쿠키/인증 헤더 전송 허용 여부
     */
    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, boolean allowCredentials) {
    }

    /**
     * WebSocket 핸드셰이크 설정.
     *
     * @param allowedOriginPatterns SockJS/STOMP 핸드셰이크를 허용할 Origin 패턴
     */
    public record Websocket(List<String> allowedOriginPatterns) {
    }

    /**
     * 결제 설정.
     *
     * @param gateway       사용할 게이트웨이 구현 키워드(stub/toss 등)
     * @param webhookSecret 웹훅 서명 검증용 시크릿. 비어 있으면 검증을 건너뛰되 경고 로그를 남긴다.
     */
    public record Payment(String gateway, String webhookSecret) {
    }

    /**
     * 매칭 정책.
     *
     * @param recommendationMaxSize 추천 API가 한 번에 반환할 수 있는 최대 건수
     * @param queueExpireMinutes    매칭 대기열 항목이 만료되기까지의 분
     */
    public record Matching(int recommendationMaxSize, int queueExpireMinutes) {
    }
}
