package com.bma.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
     * 소셜 로그인 티켓 교환 요청.
     *
     * <p>콜백이 프론트 주소로 302 하면서 넘긴 일회용 티켓을 그대로 담는다.</p>
     *
     * @param ticket 일회용 티켓
     */
    public record SocialExchangeRequest(
            @NotBlank(message = "티켓은 필수입니다.")
            String ticket
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

    /**
     * 정지 계정 로그인 실패 상세. 실패 응답의 {@code data} 에 실린다.
     *
     * <p>프론트가 이용정지 화면(S1-18~21)을 띄우는 데 필요한 정보다.</p>
     *
     * <p>{@code @JsonInclude(ALWAYS)} 를 붙인 이유: application.yml 의
     * {@code default-property-inclusion: non_null} 이 전역으로 켜져 있어
     * 그대로 두면 영구 정지일 때 {@code restrictedUntil} 필드가 통째로 사라진다.
     * 명세상 이 필드는 값이 {@code null} 이더라도 <b>항상 존재해야</b> 하므로
     * 이 DTO 에서만 전역 설정을 덮어쓴다.</p>
     *
     * @param errorCode       고정값 {@code ACCOUNT_SUSPENDED}. 봉투의 코드 체계와 별개로
     *                        명세가 요구하는 이름을 그대로 내려준다
     * @param restrictionType {@code TEMPORARY} 또는 {@code PERMANENT}
     * @param reason          정지 사유. 사용자에게 그대로 노출된다
     * @param restrictedUntil 정지 해제 일시(UTC ISO-8601). 영구 정지이면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SuspendedAccountDetail(String errorCode,
                                         String restrictionType,
                                         String reason,
                                         String restrictedUntil) {

        /** 정지 계정 오류를 나타내는 고정 코드. */
        public static final String ERROR_CODE = "ACCOUNT_SUSPENDED";

        /** 기간제 정지. */
        public static final String TEMPORARY = "TEMPORARY";

        /** 영구 정지. */
        public static final String PERMANENT = "PERMANENT";

        /**
         * 기간제 정지 상세를 만든다.
         *
         * @param reason          정지 사유
         * @param restrictedUntil 해제 일시(UTC ISO-8601 문자열)
         * @return 상세
         */
        public static SuspendedAccountDetail temporary(String reason, String restrictedUntil) {
            return new SuspendedAccountDetail(ERROR_CODE, TEMPORARY, reason, restrictedUntil);
        }

        /**
         * 영구 정지 상세를 만든다. {@code restrictedUntil} 은 항상 {@code null} 이다.
         *
         * @param reason 정지 사유
         * @return 상세
         */
        public static SuspendedAccountDetail permanent(String reason) {
            return new SuspendedAccountDetail(ERROR_CODE, PERMANENT, reason, null);
        }
    }

    /**
     * 이메일 중복 상세. 회원가입 실패 응답의 {@code data} 에 실린다.
     *
     * <p>소셜로 가입된 이메일로 일반 회원가입을 시도한 경우를 구분하기 위한 것이다.
     * 계정 자동 통합이나 별도 계정 생성은 하지 않고 차단 + 안내로 처리하며,
     * 프론트가 "카카오로 가입된 이메일이에요" 처럼 구체적으로 안내할 수 있게
     * 가입 수단을 함께 내려준다.</p>
     *
     * @param errorCode 고정값 {@code EMAIL_DUPLICATE}
     * @param provider  기존 계정의 가입 수단(LOCAL/KAKAO/NAVER/GOOGLE/APPLE)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DuplicateEmailDetail(String errorCode, String provider) {

        /** 이메일 중복 오류를 나타내는 고정 코드. */
        public static final String ERROR_CODE = "EMAIL_DUPLICATE";

        /**
         * 상세를 만든다.
         *
         * @param provider 기존 계정의 가입 수단
         * @return 상세
         */
        public static DuplicateEmailDetail of(String provider) {
            return new DuplicateEmailDetail(ERROR_CODE, provider);
        }
    }
}
