package com.bma.safety.dto;

import com.bma.safety.entity.UserReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 안전(차단/신고) API의 요청/응답 DTO 모음.
 */
public final class SafetyDtos {

    private SafetyDtos() {
    }

    /**
     * 차단 요청.
     *
     * @param reason 차단 사유(선택)
     */
    public record BlockRequest(
            @Size(max = 500, message = "차단 사유는 500자를 넘을 수 없습니다.") String reason
    ) {
    }

    /**
     * 차단 처리 결과.
     *
     * @param targetUserId 대상 사용자
     * @param blocked      차단 상태 여부
     * @param matchEnded   차단으로 인해 진행 중이던 매칭이 종료되었는지
     */
    public record BlockResult(Long targetUserId, boolean blocked, boolean matchEnded) {
    }

    /**
     * 신고 요청.
     *
     * @param reportType  신고 유형(ABUSE/FAKE/FRAUD/SEXUAL/ETC)
     * @param description 상세 내용
     * @param matchId     관련 매칭 ID(선택)
     * @param messageId   관련 메시지 ID(선택)
     */
    public record ReportRequest(
            @NotBlank(message = "신고 유형은 필수입니다.")
            @Pattern(regexp = "ABUSE|FAKE|FRAUD|SEXUAL|ETC",
                    message = "신고 유형은 ABUSE, FAKE, FRAUD, SEXUAL, ETC 중 하나여야 합니다.")
            String reportType,

            @Size(max = 2000, message = "신고 내용은 2000자를 넘을 수 없습니다.")
            String description,

            Long matchId,
            Long messageId
    ) {
    }

    /**
     * 신고 접수 결과.
     *
     * @param reportId     신고 ID
     * @param targetUserId 피신고자
     * @param reportType   신고 유형
     * @param status       처리 상태
     * @param reportedAt   접수 일시
     */
    public record ReportResponse(Long reportId,
                                 Long targetUserId,
                                 String reportType,
                                 String status,
                                 LocalDateTime reportedAt) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param report 신고 엔티티
         * @return 응답 DTO
         */
        public static ReportResponse from(UserReport report) {
            return new ReportResponse(
                    report.getId(),
                    report.getTargetUserId(),
                    report.getReportType(),
                    report.getReportStatus(),
                    report.getInsertDate());
        }
    }

    /**
     * 차단 목록 항목.
     *
     * @param targetUserId 차단한 사용자
     * @param reason       사유
     * @param blockedAt    차단 일시
     */
    public record BlockedUserResponse(Long targetUserId, String reason, LocalDateTime blockedAt) {
    }
}
