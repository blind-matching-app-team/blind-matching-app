package com.bma.verification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * S16 사진인증 API DTO (BMA-82).
 */
public final class PhotoVerificationDtos {

    private PhotoVerificationDtos() {
    }

    /**
     * 사진인증 상태 (S8-17 메뉴 표시·S16 재진입 여부).
     *
     * @param verified       완료 여부. {@code true}면 S8-17 은 "인증완료"로 표시
     * @param verifiedAt     완료 일시
     * @param provider       대조 제공자
     * @param profileImageId 인증 당시 프로필 사진. 사진을 바꾸면 인증이 해제된다
     * @param attempts       누적 시도 횟수(무제한 재시도)
     * @param lastResult     최근 시도 결과 PASS/FAIL/ERROR. 없으면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StatusResponse(boolean verified, LocalDateTime verifiedAt, String provider, Long profileImageId,
                                 long attempts, String lastResult) {
    }

    /**
     * 대조 성공 결과 (S16-05~08). 실패는 422 VERIFY_011 로 응답한다(S16-04a~d).
     *
     * @param verified   항상 {@code true}
     * @param similarity 유사도 0~100
     * @param threshold  통과 기준
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record VerifyResponse(boolean verified, double similarity, double threshold, String provider,
                                 LocalDateTime verifiedAt, Long profileImageId) {
    }
}
