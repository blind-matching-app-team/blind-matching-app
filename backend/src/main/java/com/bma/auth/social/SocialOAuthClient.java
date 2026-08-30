package com.bma.auth.social;

import com.bma.common.config.AppProperties;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 3사 OAuth 2.0 서버와 통신한다.
 *
 * <p>인가 URL 생성 → 인가 코드로 액세스 토큰 교환 → 사용자 정보 조회까지 담당하며,
 * 제공자마다 다른 응답 구조를 {@link SocialUserProfile} 로 통일해 돌려준다.</p>
 *
 * <p>클라이언트 시크릿이 서버 밖으로 나가지 않도록 백엔드 콜백 방식을 택했다.</p>
 */
@Slf4j
@Component
public class SocialOAuthClient {

    private final AppProperties properties;
    private final RestClient restClient;

    /**
     * @param properties 소셜 로그인 설정
     */
    public SocialOAuthClient(AppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /**
     * 사용자를 보낼 인가 URL 을 만든다.
     *
     * @param provider 제공자
     * @param state    CSRF 방지용 난수. 콜백에서 쿠키 값과 대조한다
     * @return 인가 URL
     * @throws BusinessException 해당 제공자의 자격 증명이 설정되지 않은 경우
     */
    public String buildAuthorizeUrl(SocialProvider provider, String state) {
        AppProperties.Oauth.Provider credentials = requireConfigured(provider);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(provider.authorizeUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", credentials.clientId())
                .queryParam("redirect_uri", redirectUri(provider))
                .queryParam("state", state);

        String scope = resolveScope(provider, credentials);
        if (scope != null) {
            builder.queryParam("scope", scope);
        }
        // encode() 가 쿼리 값의 공백과 콜론을 알아서 처리한다. 직접 인코딩하면 이중 인코딩이 된다.
        return builder.encode().build().toUriString();
    }

    /**
     * 인가 요청에 실을 scope 를 정한다.
     *
     * <p>설정값이 있으면 그것을 쓰고, 없으면 제공자 기본값을 쓴다. 콘솔에서 아직 권한을
     * 받지 못한 동의항목을 요청하면 제공자가 거부하므로(카카오 KOE205), 승인 전까지
     * 좁은 scope 로 낮춰 두고 승인 후 설정만 바꾸기 위한 장치다.</p>
     *
     * @param provider    제공자
     * @param credentials 제공자 설정
     * @return scope 문자열. 붙이지 않을 것이면 {@code null}
     */
    private String resolveScope(SocialProvider provider, AppProperties.Oauth.Provider credentials) {
        String configured = credentials.scope();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return provider.scope();
    }

    /**
     * 인가 코드를 액세스 토큰으로 교환한 뒤 사용자 정보를 조회한다.
     *
     * @param provider 제공자
     * @param code     인가 코드
     * @param state    인가 요청에 실었던 state. 네이버는 토큰 발급에도 이 값을 요구한다
     * @return 통일된 사용자 정보
     * @throws BusinessException 교환 또는 조회에 실패한 경우
     */
    public SocialUserProfile fetchProfile(SocialProvider provider, String code, String state) {
        return fetchUserInfo(provider, exchangeToken(provider, code, state));
    }

    /**
     * 3사 콘솔에 등록해야 하는 콜백 주소를 만든다.
     *
     * @param provider 제공자
     * @return 콜백 URL
     */
    public String redirectUri(SocialProvider provider) {
        return properties.oauth().redirectBaseUrl() + "/" + provider.key() + "/callback";
    }

    /**
     * 인가 코드를 액세스 토큰으로 교환한다.
     *
     * @param provider 제공자
     * @param code     인가 코드
     * @param state    인가 요청에 실었던 state
     * @return 액세스 토큰
     */
    private String exchangeToken(SocialProvider provider, String code, String state) {
        AppProperties.Oauth.Provider credentials = requireConfigured(provider);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", credentials.clientId());
        form.add("client_secret", credentials.clientSecret());
        form.add("redirect_uri", redirectUri(provider));
        form.add("code", code);
        // 표준 OAuth 2.0 에는 없지만 네이버는 토큰 발급 요청에도 state 를 요구한다.
        if (provider.tokenRequestRequiresState() && state != null) {
            form.add("state", state);
        }

        JsonNode response = postForm(provider, provider.tokenUri(), form);
        String accessToken = text(response, "access_token");

        if (accessToken == null) {
            // 응답 전문에는 코드가 그대로 들어 있을 수 있으므로 error 필드만 로그에 남긴다.
            log.warn("소셜 토큰 교환 실패: provider={}, error={}", provider, text(response, "error"));
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        return accessToken;
    }

    /**
     * 액세스 토큰으로 사용자 정보를 조회하고 제공자별 응답을 통일한다.
     *
     * @param provider    제공자
     * @param accessToken 액세스 토큰
     * @return 통일된 사용자 정보
     */
    private SocialUserProfile fetchUserInfo(SocialProvider provider, String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(provider.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("소셜 사용자 정보 조회 실패: provider={}, message={}", provider, e.getMessage());
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        if (body == null) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }

        return switch (provider) {
            case KAKAO -> parseKakao(body);
            case NAVER -> parseNaver(body);
            case GOOGLE -> parseGoogle(body);
        };
    }

    /**
     * 카카오 응답을 파싱한다.
     *
     * <p>이메일은 {@code kakao_account.email} 에 있다. 비즈 앱이 아니거나 사용자가
     * 동의하지 않으면 이메일이 없을 수 있다.</p>
     *
     * @param body 응답 본문
     * @return 사용자 정보
     */
    private SocialUserProfile parseKakao(JsonNode body) {
        JsonNode account = body.path("kakao_account");
        return new SocialUserProfile(
                SocialProvider.KAKAO,
                text(body, "id"),
                text(account, "email"),
                text(account.path("profile"), "nickname"));
    }

    /**
     * 네이버 응답을 파싱한다. 실제 값은 {@code response} 하위에 들어 있다.
     *
     * @param body 응답 본문
     * @return 사용자 정보
     */
    private SocialUserProfile parseNaver(JsonNode body) {
        JsonNode response = body.path("response");
        return new SocialUserProfile(
                SocialProvider.NAVER,
                text(response, "id"),
                text(response, "email"),
                text(response, "nickname"));
    }

    /**
     * 구글 응답을 파싱한다. OpenID Connect 표준 클레임을 쓴다.
     *
     * @param body 응답 본문
     * @return 사용자 정보
     */
    private SocialUserProfile parseGoogle(JsonNode body) {
        return new SocialUserProfile(
                SocialProvider.GOOGLE,
                text(body, "sub"),
                text(body, "email"),
                text(body, "name"));
    }

    /**
     * form-urlencoded 로 POST 한다.
     *
     * @param provider 제공자(로그용)
     * @param uri      요청 URL
     * @param form     본문
     * @return 응답 JSON
     */
    private JsonNode postForm(SocialProvider provider, String uri, MultiValueMap<String, String> form) {
        try {
            JsonNode body = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
            }
            return body;
        } catch (RestClientException e) {
            log.warn("소셜 요청 실패: provider={}, uri={}, message={}", provider, uri, e.getMessage());
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    /**
     * 제공자의 자격 증명을 가져오되, 설정되지 않았으면 거부한다.
     *
     * <p>콘솔 등록이 끝나지 않은 제공자 때문에 기동이 막히면 나머지 두 곳도 쓸 수 없으므로,
     * 기동 시점이 아니라 호출 시점에 막는다.</p>
     *
     * @param provider 제공자
     * @return 자격 증명
     * @throws BusinessException 클라이언트 ID 또는 시크릿이 비어 있는 경우
     */
    private AppProperties.Oauth.Provider requireConfigured(SocialProvider provider) {
        AppProperties.Oauth oauth = properties.oauth();
        AppProperties.Oauth.Provider credentials = switch (provider) {
            case KAKAO -> oauth.kakao();
            case NAVER -> oauth.naver();
            case GOOGLE -> oauth.google();
        };
        if (credentials == null || !credentials.isConfigured()) {
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNSUPPORTED,
                    provider.key() + " 소셜 로그인이 설정되지 않았습니다. 콘솔 등록 후 환경변수를 채워 주세요.");
        }
        return credentials;
    }

    /**
     * JSON 노드에서 문자열 필드를 꺼낸다. 없거나 null 이면 {@code null}.
     *
     * @param node  대상 노드
     * @param field 필드명
     * @return 값 또는 {@code null}
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
