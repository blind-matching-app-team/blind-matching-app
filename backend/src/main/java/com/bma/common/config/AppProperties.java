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
 * @param oauth     소셜 로그인 제공자 설정
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Jwt jwt,
        @NotNull Storage storage,
        @NotNull Cors cors,
        @NotNull Websocket websocket,
        @NotNull Payment payment,
        @NotNull Matching matching,
        @NotNull Oauth oauth
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
    public record Payment(String gateway, String webhookSecret, Toss toss) {

        /**
         * 토스페이먼츠 접속 정보.
         *
         * <p>클라이언트 키와 시크릿 키는 세트로 발급되며, 테스트({@code test_})와
         * 라이브({@code live_})를 섞어 쓰면 토스가 {@code INVALID_API_KEY}를 반환한다.
         * 실수로 라이브 키가 개발 환경에 들어오는 것을 막기 위해
         * {@link com.bma.payment.config.TossApiKeyGuard}가 기동 시점에 검증한다.</p>
         *
         * @param apiBaseUrl API 호스트. 기본값은 {@code https://api.tosspayments.com}
         * @param clientKey  클라이언트 키. 프런트엔드에 노출되는 값
         * @param secretKey  시크릿 키. 서버 전용이며 절대 저장소에 커밋하지 않는다
         */
        public record Toss(String apiBaseUrl, String clientKey, String secretKey) {
        }
    }

    /**
     * 소셜 로그인 설정.
     *
     * <p>인가 요청과 콜백을 모두 서버가 처리한다(백엔드 콜백 방식). 프론트는 버튼에서
     * {@code /api/v1/auth/social/{provider}/authorize} 로 이동시키기만 하면 된다.</p>
     *
     * @param redirectBaseUrl 3사 콘솔에 등록할 콜백 주소의 앞부분.
     *                        실제 등록값은 {@code {redirectBaseUrl}/{provider}/callback} 이다
     * @param successRedirect 로그인 성공 후 사용자를 되돌려 보낼 프론트 주소.
     *                        일회용 티켓이 {@code ?ticket=} 로 붙는다
     * @param failureRedirect 로그인 실패 시 되돌려 보낼 프론트 주소. {@code ?error=} 가 붙는다
     * @param ticketSeconds   일회용 티켓 유효 기간(초). 짧을수록 안전하다
     * @param kakao           카카오 자격 증명
     * @param naver           네이버 자격 증명
     * @param google          구글 자격 증명
     */
    public record Oauth(String redirectBaseUrl,
                        String successRedirect,
                        String failureRedirect,
                        long ticketSeconds,
                        Provider kakao,
                        Provider naver,
                        Provider google) {

        /**
         * 제공자별 자격 증명.
         *
         * <p>둘 다 비어 있으면 해당 제공자는 비활성으로 간주하고 요청을 거부한다.
         * 콘솔 등록이 끝나지 않은 제공자 때문에 기동이 막히지 않게 하기 위함이다.</p>
         *
         * @param clientId     콘솔에서 발급한 클라이언트 ID(카카오는 REST API 키)
         * @param clientSecret 콘솔에서 발급한 시크릿. 서버 전용이며 저장소에 커밋하지 않는다
         * @param scope        인가 요청에 실을 scope. 비어 있으면 제공자 기본값을 쓴다.
         *                     콘솔에서 아직 권한을 못 받은 동의항목을 요청하면 거부되므로
         *                     (카카오 KOE205) 승인 전까지 좁은 scope 로 낮춰 둘 때 쓴다
         */
        public record Provider(String clientId, String clientSecret, String scope) {

            /**
             * 사용 가능한 자격 증명인지 확인한다.
             *
             * @return 클라이언트 ID 와 시크릿이 모두 채워져 있으면 {@code true}
             */
            public boolean isConfigured() {
                return clientId != null && !clientId.isBlank()
                        && clientSecret != null && !clientSecret.isBlank();
            }
        }
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
