package com.bma.common.security;

import com.bma.common.config.AppProperties;
import com.bma.support.TestProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JwtTokenProvider} 단위 테스트.
 *
 * <p>토큰 종류 검증은 리프레시 토큰으로 일반 API를 호출하는 것을 막는 방어선이므로
 * 반드시 회귀 테스트로 고정해 둔다.</p>
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider(TestProperties.defaults(), new MockEnvironment());

    @Test
    @DisplayName("액세스 토큰에는 사용자 정보와 ACCESS 타입이 담긴다")
    void createAccessToken_containsClaims() {
        String token = provider.createAccessToken(42L, "user@example.com", "USER");

        Claims claims = provider.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class)).isEqualTo("user@example.com");
        assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).isEqualTo("USER");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class)).isEqualTo("ACCESS");
        assertThat(claims.getIssuer()).isEqualTo(JwtTokenProvider.ISSUER);
        // jti 가 있어야 개별 토큰을 추적/폐기할 수 있다.
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("리프레시 토큰을 액세스 토큰으로 사용하면 검증에 실패한다")
    void parseAndRequireType_rejectsWrongType() {
        String refreshToken = provider.createRefreshToken(1L, "user@example.com", "USER");

        assertThatThrownBy(() -> provider.parseAndRequireType(refreshToken, TokenType.ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("서명 키가 다르면 검증에 실패한다")
    void parse_rejectsTokenSignedWithAnotherKey() {
        AppProperties otherProperties = new AppProperties(
                new AppProperties.Jwt("another-secret-key-value-0123456789-abcdef", 1800, 1209600),
                TestProperties.defaults().storage(),
                TestProperties.defaults().cors(),
                TestProperties.defaults().websocket(),
                TestProperties.defaults().payment(),
                TestProperties.defaults().matching());
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties, new MockEnvironment());

        String foreignToken = otherProvider.createAccessToken(1L, "user@example.com", "USER");

        assertThatThrownBy(() -> provider.parse(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("시크릿이 32바이트 미만이면 기동 시점에 실패한다")
    void constructor_rejectsShortSecret() {
        AppProperties shortSecret = new AppProperties(
                new AppProperties.Jwt("too-short", 1800, 1209600),
                TestProperties.defaults().storage(),
                TestProperties.defaults().cors(),
                TestProperties.defaults().websocket(),
                TestProperties.defaults().payment(),
                TestProperties.defaults().matching());

        assertThatThrownBy(() -> new JwtTokenProvider(shortSecret, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("운영 프로파일에서 개발용 기본 시크릿을 쓰면 기동을 막는다")
    void constructor_rejectsDevSecretOnProdProfile() {
        AppProperties devSecret = new AppProperties(
                new AppProperties.Jwt("change-this-development-secret-key-at-least-32-bytes",
                        1800, 1209600),
                TestProperties.defaults().storage(),
                TestProperties.defaults().cors(),
                TestProperties.defaults().websocket(),
                TestProperties.defaults().payment(),
                TestProperties.defaults().matching());

        MockEnvironment prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtTokenProvider(devSecret, prodEnvironment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("비운영 프로파일에서는 개발용 기본 시크릿을 경고만 남기고 허용한다")
    void constructor_allowsDevSecretOnNonProdProfile() {
        AppProperties devSecret = new AppProperties(
                new AppProperties.Jwt("change-this-development-secret-key-at-least-32-bytes",
                        1800, 1209600),
                TestProperties.defaults().storage(),
                TestProperties.defaults().cors(),
                TestProperties.defaults().websocket(),
                TestProperties.defaults().payment(),
                TestProperties.defaults().matching());

        MockEnvironment localEnvironment = new MockEnvironment();
        localEnvironment.setActiveProfiles("local");

        JwtTokenProvider localProvider = new JwtTokenProvider(devSecret, localEnvironment);

        assertThat(localProvider.accessTokenSeconds()).isEqualTo(1800);
        assertThat(List.of(localProvider.createAccessToken(1L, "a@b.com", "USER"))).hasSize(1);
    }
}
