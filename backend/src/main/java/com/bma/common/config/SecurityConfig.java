package com.bma.common.config;

import com.bma.common.security.JwtAccessDeniedHandler;
import com.bma.common.security.JwtAuthenticationEntryPoint;
import com.bma.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * HTTP 보안 설정.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li>401/403을 구분해 JSON으로 응답하도록 엔트리 포인트와 핸들러를 등록했다.</li>
 *   <li>CORS 정책을 설정 파일 기반으로 추가했다(기존에는 아예 없어 웹 클라이언트 연동이 불가능했다).</li>
 *   <li>결제 웹훅은 외부 PG가 호출하므로 인증 대상에서 제외하고, 대신 서명 검증으로 보호한다.</li>
 *   <li>관리자 전용 경로에 ROLE_ADMIN을 요구하도록 했다(기존에는 role이 전혀 쓰이지 않았다).</li>
 *   <li>보안 헤더(HSTS, X-Frame-Options 등)를 명시적으로 켰다.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 인증 없이 접근 가능한 경로. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            // 소셜 로그인. authorize 는 브라우저 이동, callback 은 3사가 호출하므로
            // JWT 를 가질 수 없다. exchange 는 티켓 자체가 자격 증명이다.
            "/api/v1/auth/social/**",
            // 외부 PG가 호출하므로 JWT를 가질 수 없다. 서명 검증으로 보호한다.
            "/api/v1/payments/webhook",
            // STOMP 핸드셰이크. 실제 인증은 CONNECT 프레임에서 수행한다.
            "/ws/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**"
    };

    private final AppProperties properties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    /**
     * 비밀번호 해시 인코더.
     *
     * @return BCrypt 인코더
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 보안 필터 체인.
     *
     * @param http 보안 설정 빌더
     * @return 구성된 필터 체인
     * @throws Exception 설정 오류
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 토큰 기반 무상태 API이므로 CSRF 토큰이 필요 없다.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 기본 로그인 폼/HTTP Basic 은 사용하지 않는다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        // 프리플라이트 요청은 인증 대상이 아니다.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // health 이외의 actuator 엔드포인트는 관리자만 볼 수 있다.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * CORS 정책.
     *
     * <p>자격 증명을 함께 보내는 경우 {@code *} 와일드카드를 쓸 수 없으므로
     * 허용 Origin은 설정 파일에 명시적으로 나열한다.</p>
     *
     * @return CORS 설정 소스
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        AppProperties.Cors corsProperties = properties.cors();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods());
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
