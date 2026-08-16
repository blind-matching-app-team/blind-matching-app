package com.bma.auth.controller;

import com.bma.auth.dto.AuthDtos.LoginRequest;
import com.bma.auth.dto.AuthDtos.RefreshRequest;
import com.bma.auth.dto.AuthDtos.SignupRequest;
import com.bma.auth.dto.AuthDtos.SignupResponse;
import com.bma.auth.dto.AuthDtos.TokenResponse;
import com.bma.auth.service.AuthService;
import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API.
 *
 * <p>{@code /signup}, {@code /login}, {@code /refresh}는 비인증 경로이고
 * {@code /logout}은 인증이 필요하다. 로그아웃을 인증 경로로 옮긴 이유는
 * 리프레시 토큰 문자열만 알면 누구나 타인의 세션을 끊을 수 있었기 때문이다.</p>
 */
@Tag(name = "Auth", description = "회원가입 및 토큰 발급")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입.
     *
     * @param request 가입 요청
     * @return 생성된 사용자 정보
     */
    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    /**
     * 로그인.
     *
     * @param request 로그인 요청
     * @return 액세스/리프레시 토큰
     */
    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 액세스 토큰 재발급.
     *
     * @param request 리프레시 토큰
     * @return 새로 발급된 토큰 쌍
     */
    @Operation(summary = "토큰 재발급", description = "리프레시 토큰은 사용 즉시 회수되고 새 토큰으로 교체된다.")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * 로그아웃.
     *
     * @param principal 인증 주체
     * @param request   회수할 리프레시 토큰
     * @return 빈 성공 응답
     */
    @Operation(summary = "로그아웃", description = "본인 소유의 리프레시 토큰만 회수된다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal CustomUserPrincipal principal,
                                    @Valid @RequestBody RefreshRequest request) {
        authService.logout(principal.userId(), request.refreshToken());
        return ApiResponse.ok();
    }
}
