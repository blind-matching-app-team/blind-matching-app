package com.bma.verification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * S15 본인인증 API DTO (BMA-79).
 */
public final class IdentityVerificationDtos {

    private IdentityVerificationDtos() {
    }

    /**
     * 인증 요청 결과 (S15-04 버튼 → SDK 호출 파라미터).
     *
     * @param transactionId 거래 ID. confirm 때 그대로 돌려준다
     * @param provider      인증사 키워드
     * @param sdkParams     업체별 SDK 파라미터(토큰·URL 등)
     * @param expiresAt     요청 만료 일시(기본 10분)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RequestResponse(String transactionId, String provider, Map<String, Object> sdkParams,
                                  LocalDateTime expiresAt) {
    }

    /**
     * 인증 결과 확인 요청.
     *
     * @param transactionId   요청 때 받은 거래 ID
     * @param providerPayload SDK 가 돌려준 결과를 그대로(업체별). 스텁은 {@code {"result":{"name","birthDate","genderCode","phoneNumber"}}}
     */
    public record ConfirmRequest(
            @NotBlank(message = "transactionId 는 필수입니다.")
            String transactionId,
            Map<String, Object> providerPayload
    ) {
    }

    /**
     * 본인인증 상태 (S4 "매칭 시작하기" 분기, S15 재노출 여부).
     *
     * @param verified        완료 여부. {@code true}면 S15 를 건너뛰고 S9 로
     * @param verifiedAt      완료 일시
     * @param provider        인증사
     * @param matchingAllowed 매칭 진입 가능 여부(= verified)
     * @param lastAttemptStatus 최근 시도 상태(REQUESTED/VERIFIED/REJECTED_MINOR/...). 없으면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StatusResponse(boolean verified, LocalDateTime verifiedAt, String provider,
                                 boolean matchingAllowed, String lastAttemptStatus) {
    }

    /**
     * 확인 결과 (성공 시). 미성년자는 403 VERIFY_003 으로 실패한다.
     *
     * @param status     VERIFIED
     * @param verifiedAt 완료 일시
     * @param adult      성인 여부(항상 true)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConfirmResponse(String status, LocalDateTime verifiedAt, String provider, boolean adult,
                                  boolean matchingAllowed) {
    }
}
