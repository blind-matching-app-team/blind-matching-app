package com.bma.auth.social;

import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;

import java.util.Arrays;

/**
 * 지원하는 소셜 로그인 제공자와 각 제공자의 OAuth 2.0 엔드포인트.
 *
 * <p>{@code US_USER.LOGIN_PROVIDER} 에 저장되는 값이 {@link #name()} 과 같도록 맞춰 두었다.
 * 스키마 주석에는 APPLE 도 있으나 이번 범위(BMA-29)에서는 3사만 다룬다.</p>
 */
public enum SocialProvider {

    /** 카카오. 이메일을 필수 동의로 받으려면 비즈 앱 전환과 검수가 필요하다. */
    KAKAO("https://kauth.kakao.com/oauth/authorize",
            "https://kauth.kakao.com/oauth/token",
            "https://kapi.kakao.com/v2/user/me",
            "account_email",
            false),

    /** 네이버. 콘솔의 제공 정보에서 이메일 주소를 체크해야 한다. */
    NAVER("https://nid.naver.com/oauth2.0/authorize",
            "https://nid.naver.com/oauth2.0/token",
            "https://openapi.naver.com/v1/nid/me",
            null,
            // 네이버만 토큰 발급 요청에도 state 를 요구한다. 빼면 교환이 실패한다.
            true),

    /** 구글. email/profile 은 민감 스코프가 아니라 별도 검증 없이 게시할 수 있다. */
    GOOGLE("https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://www.googleapis.com/oauth2/v3/userinfo",
            "openid email profile",
            false);

    private final String authorizeUri;
    private final String tokenUri;
    private final String userInfoUri;
    private final String scope;
    private final boolean tokenRequestRequiresState;

    SocialProvider(String authorizeUri, String tokenUri, String userInfoUri,
                   String scope, boolean tokenRequestRequiresState) {
        this.authorizeUri = authorizeUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        this.scope = scope;
        this.tokenRequestRequiresState = tokenRequestRequiresState;
    }

    /**
     * 경로 변수로 들어온 문자열을 제공자로 변환한다.
     *
     * @param value {@code kakao} / {@code naver} / {@code google} (대소문자 무시)
     * @return 제공자
     * @throws BusinessException 지원하지 않는 값인 경우
     */
    public static SocialProvider from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SOCIAL_PROVIDER_UNSUPPORTED, "지원하지 않는 소셜 제공자입니다: " + value));
    }

    /** @return 인가 요청 URL */
    public String authorizeUri() {
        return authorizeUri;
    }

    /** @return 토큰 교환 URL */
    public String tokenUri() {
        return tokenUri;
    }

    /** @return 사용자 정보 조회 URL */
    public String userInfoUri() {
        return userInfoUri;
    }

    /** @return 인가 요청에 붙일 scope. 필요 없으면 {@code null} */
    public String scope() {
        return scope;
    }

    /**
     * @return 토큰 발급 요청에도 {@code state} 를 실어야 하면 {@code true}.
     *         표준 OAuth 2.0 에는 없는 요구지만 네이버가 이를 요구한다
     */
    public boolean tokenRequestRequiresState() {
        return tokenRequestRequiresState;
    }

    /** @return 경로 변수와 리다이렉트 URI 에 쓰는 소문자 키 */
    public String key() {
        return name().toLowerCase();
    }
}
