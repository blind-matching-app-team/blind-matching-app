package com.bma.common.security;

import com.bma.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급 및 검증 컴포넌트.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li>운영 프로파일에서 개발용 기본 시크릿을 그대로 쓰면 기동을 중단한다(fail-fast).</li>
 *   <li>토큰마다 {@code jti}를 부여해 개별 토큰 추적/폐기가 가능하도록 했다.</li>
 *   <li>{@code iss} 클레임을 넣어 다른 시스템의 토큰이 섞여 들어오는 것을 막는다.</li>
 *   <li>시계 오차 30초를 허용해 서버 간 시간 차로 인한 오검증을 줄인다.</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 토큰 발급자 식별자. 검증 시 동일 값인지 확인한다. */
    public static final String ISSUER = "blind-matching-api";

    /** 토큰 종류를 담는 커스텀 클레임 키. */
    public static final String CLAIM_TOKEN_TYPE = "typ";

    /** 이메일을 담는 커스텀 클레임 키. */
    public static final String CLAIM_EMAIL = "email";

    /** 권한 코드를 담는 커스텀 클레임 키. */
    public static final String CLAIM_ROLE = "role";

    /** HS256 서명에 필요한 최소 키 길이(바이트). */
    private static final int MIN_SECRET_BYTES = 32;

    /** application.yml에 예시로 들어 있는 개발용 시크릿. 운영에서 이 값이면 기동을 막는다. */
    private static final String DEV_PLACEHOLDER_SECRET = "change-this-development-secret-key-at-least-32-bytes";

    /** 서버 간 시계 오차 허용치(초). */
    private static final long CLOCK_SKEW_SECONDS = 30L;

    private final SecretKey signingKey;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;

    /**
     * 설정 값을 검증하고 서명 키를 준비한다.
     *
     * @param properties  애플리케이션 설정
     * @param environment 활성 프로파일 확인용
     * @throws IllegalStateException 시크릿이 없거나 짧거나, 운영에서 개발용 기본값을 쓰는 경우
     */
    public JwtTokenProvider(AppProperties properties, Environment environment) {
        String secret = properties.jwt().secret();
        validateSecret(secret, environment);

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenSeconds = properties.jwt().accessTokenSeconds();
        this.refreshTokenSeconds = properties.jwt().refreshTokenSeconds();
    }

    /**
     * 액세스 토큰을 발급한다.
     *
     * @param userId 사용자 ID
     * @param email  이메일
     * @param role   권한 코드
     * @return 서명된 JWT 문자열
     */
    public String createAccessToken(Long userId, String email, String role) {
        return create(userId, email, role, TokenType.ACCESS, accessTokenSeconds);
    }

    /**
     * 리프레시 토큰을 발급한다.
     *
     * @param userId 사용자 ID
     * @param email  이메일
     * @param role   권한 코드
     * @return 서명된 JWT 문자열
     */
    public String createRefreshToken(Long userId, String email, String role) {
        return create(userId, email, role, TokenType.REFRESH, refreshTokenSeconds);
    }

    /**
     * 토큰 서명과 만료를 검증하고 클레임을 반환한다.
     *
     * @param token 검증할 JWT
     * @return 페이로드 클레임
     * @throws JwtException 서명 불일치, 만료, 발급자 불일치 등 검증 실패 시
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰을 검증하고 기대하는 종류인지까지 확인한다.
     *
     * <p>리프레시 토큰으로 API를 호출하거나, 액세스 토큰으로 재발급을 시도하는 것을 막는다.</p>
     *
     * @param token    검증할 JWT
     * @param expected 기대하는 토큰 종류
     * @return 페이로드 클레임
     * @throws JwtException 검증 실패 또는 종류 불일치
     */
    public Claims parseAndRequireType(String token, TokenType expected) {
        Claims claims = parse(token);
        String actual = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expected.value().equals(actual)) {
            throw new JwtException("기대한 토큰 종류가 아닙니다. expected=" + expected + ", actual=" + actual);
        }
        return claims;
    }

    /** 액세스 토큰 유효 기간(초). 클라이언트에 만료 시각을 알려줄 때 사용한다. */
    public long accessTokenSeconds() {
        return accessTokenSeconds;
    }

    /** 리프레시 토큰 유효 기간(초). DB의 만료 일시 계산에 사용한다. */
    public long refreshTokenSeconds() {
        return refreshTokenSeconds;
    }

    /**
     * 공통 토큰 생성 로직.
     *
     * @param userId       주체(sub)로 들어갈 사용자 ID
     * @param email        이메일 클레임
     * @param role         권한 클레임
     * @param type         토큰 종류
     * @param validSeconds 유효 기간(초)
     * @return 서명된 JWT 문자열
     */
    private String create(Long userId, String email, String role, TokenType type, long validSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, type.value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 서명 키 설정의 안전성을 검사한다.
     *
     * @param secret      설정된 시크릿
     * @param environment 활성 프로파일 확인용
     */
    private void validateSecret(String secret, Environment environment) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret 이 비어 있습니다. JWT_SECRET 환경변수를 설정하세요.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 은 최소 " + MIN_SECRET_BYTES + "바이트여야 합니다. 현재 길이: "
                            + secret.getBytes(StandardCharsets.UTF_8).length);
        }

        boolean productionProfile = environment.matchesProfiles("prod", "production");
        if (DEV_PLACEHOLDER_SECRET.equals(secret)) {
            if (productionProfile) {
                throw new IllegalStateException(
                        "운영 프로파일에서 개발용 기본 JWT 시크릿을 사용할 수 없습니다. JWT_SECRET 환경변수를 설정하세요.");
            }
            log.warn("개발용 기본 JWT 시크릿을 사용 중입니다. 운영 배포 전 반드시 JWT_SECRET 를 교체하세요.");
        }
    }
}
