package com.bma.auth.controller;

import com.bma.auth.dto.AuthDtos.DuplicateEmailDetail;
import com.bma.auth.dto.AuthDtos.SocialExchangeRequest;
import com.bma.auth.dto.AuthDtos.TokenResponse;
import com.bma.auth.service.SocialAuthService;
import com.bma.auth.social.SocialProvider;
import com.bma.common.exception.BusinessException;
import com.bma.common.config.AppProperties;
import com.bma.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * 소셜 로그인 API (백엔드 콜백 방식).
 *
 * <p>프론트는 버튼에서 {@code /authorize} 로 이동시키기만 하면 된다. 3사 인가 화면 이동과
 * 콜백 수신을 모두 서버가 처리하므로 클라이언트 시크릿이 브라우저에 노출되지 않는다.</p>
 *
 * <p>로그인이 끝나면 프론트 주소로 302 하며 <b>일회용 티켓</b>만 쿼리로 넘긴다.
 * JWT 를 URL 에 실으면 브라우저 히스토리와 리퍼러에 남기 때문이다.
 * 프론트는 받은 티켓을 {@code /exchange} 로 제출해 실제 토큰을 받는다.</p>
 */
@Slf4j
@Tag(name = "Auth - Social", description = "카카오/네이버/구글 로그인")
@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    /** state 를 담아 두는 쿠키 이름. */
    private static final String STATE_COOKIE = "bma_oauth_state";

    /** state 쿠키 수명. 인가 화면에서 머무는 시간을 감안해 5분으로 둔다. */
    private static final Duration STATE_COOKIE_TTL = Duration.ofMinutes(5);

    private final SocialAuthService socialAuthService;
    private final AppProperties properties;

    /**
     * 3사 인가 화면으로 보낸다.
     *
     * <p>CSRF 방지용 state 를 만들어 HttpOnly 쿠키에 심고 같은 값을 인가 요청에 붙인다.
     * 콜백에서 두 값을 대조한다.</p>
     *
     * @param provider 제공자 경로 변수(kakao/naver/google)
     * @param response state 쿠키를 심을 응답
     * @return 3사 인가 URL 로의 302
     */
    @Operation(summary = "소셜 인가 화면으로 이동", description = "프론트는 이 URL 로 이동시키기만 하면 된다.")
    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider, HttpServletResponse response) {
        SocialProvider socialProvider = SocialProvider.from(provider);
        String state = socialAuthService.createState();

        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(state, STATE_COOKIE_TTL).toString());

        return ResponseEntity.status(302)
                .location(URI.create(socialAuthService.buildAuthorizeUrl(socialProvider, state)))
                .build();
    }

    /**
     * 3사가 호출하는 콜백.
     *
     * <p>이 엔드포인트는 브라우저 이동으로 호출되므로 JSON 오류를 돌려줘도 사용자가 볼 수 없다.
     * 성공/실패 모두 프론트 주소로 302 하고 결과를 쿼리로 알린다.</p>
     *
     * @param provider    제공자 경로 변수
     * @param code        인가 코드. 사용자가 동의를 취소하면 없을 수 있다
     * @param state       3사가 되돌려준 state
     * @param error       3사가 보낸 오류 코드(동의 취소 등)
     * @param cookieState 쿠키에 심어 두었던 state
     * @param response    state 쿠키를 지울 응답
     * @return 프론트 주소로의 302
     */
    @Operation(summary = "소셜 콜백", description = "3사가 호출한다. 프론트가 직접 호출하지 않는다.")
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         @CookieValue(name = STATE_COOKIE, required = false) String cookieState,
                                         HttpServletResponse response) {
        // 일회용이므로 성공/실패와 무관하게 즉시 만료시킨다.
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO).toString());

        if (error != null || code == null) {
            log.info("소셜 인가 취소 또는 오류: provider={}, error={}", provider, error);
            return redirect(failureUrl("SOCIAL_AUTH_CANCELED", null));
        }

        try {
            SocialProvider socialProvider = SocialProvider.from(provider);
            String ticket = socialAuthService.handleCallback(socialProvider, code, state, cookieState);
            return redirect(successUrl(ticket));
        } catch (BusinessException e) {
            log.warn("소셜 로그인 실패: provider={}, code={}", provider, e.getErrorCode().getCode());
            return redirect(failureUrl(e.getErrorCode().getCode(), signupProviderOf(e)));
        }
    }

    /**
     * 일회용 티켓을 실제 토큰으로 교환한다.
     *
     * @param request 티켓
     * @return 이메일 로그인과 동일한 토큰 응답
     */
    @Operation(summary = "로그인 티켓 교환", description = "콜백이 넘긴 일회용 티켓을 액세스/리프레시 토큰으로 바꾼다.")
    @PostMapping("/exchange")
    public ApiResponse<TokenResponse> exchange(@Valid @RequestBody SocialExchangeRequest request) {
        return ApiResponse.ok(socialAuthService.exchangeTicket(request.ticket()));
    }

    /**
     * state 쿠키를 만든다.
     *
     * <p>{@code SameSite=Lax} 인 이유: 3사에서 우리 콜백으로 돌아오는 것은 최상위 GET 이동이라
     * Lax 로도 쿠키가 함께 전송된다. None 으로 열면 불필요하게 노출 범위가 넓어진다.</p>
     *
     * @param value 쿠키 값
     * @param ttl   수명. {@link Duration#ZERO} 이면 즉시 삭제된다
     * @return 쿠키
     */
    private ResponseCookie stateCookie(String value, Duration ttl) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth/social")
                .sameSite("Lax")
                .maxAge(ttl)
                .build();
    }

    /**
     * 성공 시 이동할 프론트 주소를 만든다.
     *
     * @param ticket 일회용 티켓
     * @return 리다이렉트 URL
     */
    private String successUrl(String ticket) {
        return UriComponentsBuilder.fromUriString(properties.oauth().successRedirect())
                .queryParam("ticket", ticket)
                .encode()
                .build()
                .toUriString();
    }

    /**
     * 실패 시 이동할 프론트 주소를 만든다.
     *
     * <p>이메일 중복은 "카카오로 가입된 이메일이에요"처럼 구체적으로 안내해야 하므로
     * 기존 계정의 가입 수단을 함께 실어 보낸다. 콜백은 302 라서 JSON 본문을 줄 수 없다.</p>
     *
     * @param errorCode      오류 코드
     * @param signupProvider 기존 계정의 가입 수단. 해당 없으면 {@code null}
     * @return 리다이렉트 URL
     */
    private String failureUrl(String errorCode, String signupProvider) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.oauth().failureRedirect())
                .queryParam("error", errorCode);
        if (signupProvider != null) {
            builder.queryParam("provider", signupProvider);
        }
        return builder.encode().build().toUriString();
    }

    /**
     * 이메일 중복 오류에서 기존 계정의 가입 수단을 꺼낸다.
     *
     * <p>정지 계정 상세({@code restrictionType}/{@code reason}/{@code restrictedUntil})는
     * 사유 텍스트가 길어 쿼리 파라미터로 넘기기에 적절하지 않다. 그 경우 프론트는
     * 이메일 로그인으로 유도해 완전한 응답을 받게 한다(AUTH_API.md 참고).</p>
     *
     * @param e 업무 예외
     * @return 가입 수단. 이메일 중복이 아니면 {@code null}
     */
    private String signupProviderOf(BusinessException e) {
        return e.getDetails() instanceof DuplicateEmailDetail detail ? detail.provider() : null;
    }

    /**
     * 302 응답을 만든다.
     *
     * @param url 이동할 주소
     * @return 302 응답
     */
    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }
}
