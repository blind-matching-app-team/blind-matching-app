package com.bma.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

/**
 * JWT에서 복원한 인증 주체.
 *
 * <p>DB 조회 없이 토큰 클레임만으로 구성되므로 요청당 추가 쿼리가 발생하지 않는다.
 * 대신 사용자 상태 변경(정지/탈퇴)은 액세스 토큰 만료 시간(기본 30분)만큼 반영이 지연된다.</p>
 *
 * @param userId 사용자 ID
 * @param email  로그인 이메일
 * @param role   권한 코드(USER/ADMIN)
 */
public record CustomUserPrincipal(Long userId, String email, String role)
        implements UserDetails, Serializable {

    /** Spring Security 권한 문자열의 관례적 접두사. */
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * STOMP 메시지 핸들러가 받은 {@link Principal}에서 인증 주체를 꺼낸다.
     *
     * <p>WebSocket 경로에는 {@code spring-security-messaging}의
     * {@code @AuthenticationPrincipal} 리졸버가 없으므로 직접 변환한다.</p>
     *
     * @param principal STOMP 세션에 연결된 Principal
     * @return 인증 주체
     * @throws IllegalStateException 인증되지 않은 세션인 경우
     */
    public static CustomUserPrincipal from(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new IllegalStateException("인증되지 않은 WebSocket 세션입니다.");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
    }

    /**
     * {@inheritDoc}
     *
     * <p>비밀번호는 토큰 기반 인증에서 사용하지 않으므로 항상 빈 문자열을 돌려준다.
     * 절대 실제 해시를 담지 않는다.</p>
     */
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
