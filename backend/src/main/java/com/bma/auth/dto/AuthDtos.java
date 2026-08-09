package com.bma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 인증 API의 요청/응답 DTO 모음.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * 회원가입 요청.
     *
     * <p>고친 점: 기존에는 {@code password}에 {@code @Size}만 있고 {@code @NotBlank}가 없어
     * null 비밀번호가 검증을 통과한 뒤 인코딩 단계에서 NPE(500)로 터졌다.</p>
     *
     * @param email       로그인 이메일
     * @param password    비밀번호(영문 + 숫자 조합 8자 이상)
     * @param phoneNumber 휴대전화 번호(선택)
     */
    public record SignupRequest(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
            String email,

            @NotBlank(message = "비밀번호는 필수입니다.")
            @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
            String password,

            @Pattern(
                    regexp = "^$|^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
                    message = "휴대전화 번호 형식이 올바르지 않습니다.")
            String phoneNumber
    ) {
    }

    /**
     * 로그인 요청.
     *
     * @param email    로그인 이메일
     * @param password 비밀번호
     */
    public record LoginRequest(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            String email,

            @NotBlank(message = "비밀번호는 필수입니다.")
            String password
    ) {
    }

    /**
     * 토큰 재발급/로그아웃 요청.
     *
     * @param refreshToken 리프레시 토큰 원문
     */
    public record RefreshRequest(
            @NotBlank(message = "리프레시 토큰은 필수입니다.")
            String refreshToken
    ) {
    }

    /**
     * 토큰 발급 응답.
     *
     * @param accessToken      액세스 토큰
     * @param refreshToken     리프레시 토큰(재발급 시 항상 새 값으로 교체된다)
     * @param expiresIn        액세스 토큰 유효 기간(초)
     * @param userId           사용자 ID
     * @param profileCompleted 프로필 작성 완료 여부. 클라이언트가 온보딩 화면으로 보낼지 판단한다.
     */
    public record TokenResponse(String accessToken,
                                String refreshToken,
                                long expiresIn,
                                Long userId,
                                boolean profileCompleted) {
    }

    /**
     * 회원가입 응답.
     *
     * @param userId 생성된 사용자 ID
     * @param status 계정 상태
     */
    public record SignupResponse(Long userId, String status) {
    }
}
