package com.bma.common.security;

import com.bma.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization 헤더의 Bearer 액세스 토큰을 검증해 SecurityContext를 채우는 필터.
 *
 * <p>고친 점: 기존 구현은 {@code catch (Exception ignored) {}} 로 모든 예외를 삼켜서
 * 만료 토큰과 위조 토큰, 권한 부족이 전부 403으로 뭉뚱그려졌고 로그도 남지 않았다.
 * 이제 실패 사유를 요청 속성에 담아 {@link JwtAuthenticationEntryPoint}가
 * 401 + 구체적인 에러 코드로 응답하도록 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 토큰 검증 실패 사유를 엔트리 포인트로 전달할 때 쓰는 요청 속성 키. */
    public static final String ATTRIBUTE_ERROR_CODE = "com.bma.jwt.errorCode";

    /** Bearer 토큰 접두사. */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // 토큰이 아예 없으면 익명 요청으로 흘려보낸다.
        // 보호된 경로라면 이후 인가 단계에서 엔트리 포인트가 401을 내려준다.
        if (token != null) {
            try {
                authenticate(token);
            } catch (ExpiredJwtException e) {
                // 클라이언트가 "재발급이 필요한 상황"임을 구분할 수 있어야 자동 갱신 로직을 짤 수 있다.
                request.setAttribute(ATTRIBUTE_ERROR_CODE, ErrorCode.TOKEN_EXPIRED);
                log.debug("만료된 액세스 토큰 요청: {}", request.getRequestURI());
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(ATTRIBUTE_ERROR_CODE, ErrorCode.INVALID_TOKEN);
                log.debug("유효하지 않은 액세스 토큰 요청: {} ({})", request.getRequestURI(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 토큰을 검증하고 인증 객체를 SecurityContext에 저장한다.
     *
     * @param token Bearer 접두사를 제거한 JWT
     */
    private void authenticate(String token) {
        // 리프레시 토큰으로 일반 API를 호출하는 것을 막기 위해 종류까지 확인한다.
        Claims claims = tokenProvider.parseAndRequireType(token, TokenType.ACCESS);

        CustomUserPrincipal principal = new CustomUserPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class),
                claims.get(JwtTokenProvider.CLAIM_ROLE, String.class));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Authorization 헤더에서 Bearer 토큰을 꺼낸다.
     *
     * @param request 현재 요청
     * @return 토큰 문자열. 헤더가 없거나 형식이 다르면 {@code null}
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
