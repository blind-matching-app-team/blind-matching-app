package com.bma.common.config;

import com.bma.common.security.CustomUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * JPA Auditing 활성화 및 감사 주체(auditor) 결정 전략.
 *
 * <p>기존에는 {@code @EnableJpaAuditing}만 켜 두고 {@link AuditorAware} 빈이 없어
 * {@code INSERT_USER}/{@code UPDATE_USER}가 채워지지 않았다. 여기서 SecurityContext의
 * 인증 주체로부터 사용자 ID를 꺼내 감사 컬럼에 기록한다.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditConfig {

    /** 인증 정보가 없는 배치/시스템 작업에서 사용할 감사 주체 값. */
    private static final String SYSTEM_AUDITOR = "SYSTEM";

    /**
     * 현재 요청의 인증 주체를 감사 컬럼 값으로 변환한다.
     *
     * <p>비로그인 요청(회원가입 등)이나 스케줄러에서 저장하는 경우에는 "SYSTEM"을 사용한다.</p>
     *
     * @return 감사 주체 제공자
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of(SYSTEM_AUDITOR);
            }
            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                return Optional.of(String.valueOf(principal.userId()));
            }
            return Optional.of(SYSTEM_AUDITOR);
        };
    }
}
