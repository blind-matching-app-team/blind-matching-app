package com.bma.safety.dto;

import com.bma.safety.entity.UserReport;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
     * <p>BMA-30: 차단하면 차단자만 채팅방에서 나가고 상대는 아무 변화가 없다(차단 사실 비노출).
     * 매칭 행은 그대로 두되 차단자의 매칭 목록에서는 빠지고, 매칭 알고리즘에서 서로 영구 제외된다.</p>
     *
     * @param targetUserId 대상 사용자
     * @param blocked      차단 상태 여부
     * @param chatRoomLeft 차단으로 내가 나간 채팅방이 있었는지
     */
    public record BlockResult(Long targetUserId, boolean blocked, boolean chatRoomLeft) {
    }

    /**
     * 신고 요청 (S11-10~12 신고하기 모달).
     *
     * @param targetUserId 피신고자 ID. {@code POST /reports} 에서 필수, {@code POST /users/{id}/report} 에서는 경로값을 쓴다
     * @param reportType   신고 유형(ABUSE 욕설/FAKE 허위프로필/FRAUD 사기/SEXUAL 부적절한 콘텐츠/ETC 기타)
     * @param description  상세 사유(선택, S11-11)
     * @param matchId      관련 매칭 ID(선택). 생략하면 두 사람의 매칭을 찾아 채운다
     * @param messageId    관련 메시지 ID(선택). 지정하면 피신고자가 보낸 메시지여야 한다
     */
    public record ReportRequest(
            Long targetUserId,

            @NotBlank(message = "신고 유형은 필수입니다.")
            @Pattern(regexp = "ABUSE|FAKE|FRAUD|SEXUAL|ETC",
                    message = "신고 유형은 ABUSE, FAKE, FRAUD, SEXUAL, ETC 중 하나여야 합니다.")
            String reportType,

            @Size(max = 2000, message = "신고 내용은 2000자를 넘을 수 없습니다.")
            String description,

            Long matchId,
            Long messageId
    ) {
        /**
         * 경로의 피신고자 ID 를 채운 사본을 만든다.
         *
         * @param targetUserId 피신고자
         * @return 대상이 채워진 요청
         */
        public ReportRequest withTarget(Long targetUserId) {
            return new ReportRequest(targetUserId, reportType, description, matchId, messageId);
        }
    }

    /**
     * {@code POST /reports} 전용 요청. 대상 ID 가 본문에 있어야 한다.
     *
     * @param targetUserId 피신고자 ID(필수)
     * @param reportType   신고 유형
     * @param description  상세 사유(선택)
     * @param matchId      관련 매칭 ID(선택)
     * @param messageId    관련 메시지 ID(선택)
     */
    public record CreateReportRequest(
            @NotNull(message = "피신고자 ID 는 필수입니다.")
            Long targetUserId,

            @NotBlank(message = "신고 유형은 필수입니다.")
            @Pattern(regexp = "ABUSE|FAKE|FRAUD|SEXUAL|ETC",
                    message = "신고 유형은 ABUSE, FAKE, FRAUD, SEXUAL, ETC 중 하나여야 합니다.")
            String reportType,

            @Size(max = 2000, message = "신고 내용은 2000자를 넘을 수 없습니다.")
            String description,

            Long matchId,
            Long messageId
    ) {
        /**
         * 서비스가 쓰는 공통 요청으로 바꾼다.
         *
         * @return 공통 요청
         */
        public ReportRequest toReportRequest() {
            return new ReportRequest(targetUserId, reportType, description, matchId, messageId);
        }
    }

    /**
     * 신고 접수 결과 (S11-12 완료 토스트).
     *
     * @param reportId        신고 ID
     * @param targetUserId    피신고자
     * @param reportType      신고 유형
     * @param severity        심각도 NORMAL/SEVERE
     * @param status          처리 상태 COUNTED(자동 반영)/PENDING_REVIEW(관리자 검토 대기)
     * @param immediateReview 즉시 관리자 검토 큐(S12)로 넘어갔는지 — 사기·부적절한 콘텐츠
     * @param matchId         연결된 매칭. 없으면 {@code null}
     * @param chatRoomLeft    신고로 내가 채팅방에서 나갔는지(상대는 무변화)
     * @param reportedAt      접수 일시
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReportResponse(Long reportId,
                                 Long targetUserId,
                                 String reportType,
                                 String severity,
                                 String status,
                                 boolean immediateReview,
                                 Long matchId,
                                 boolean chatRoomLeft,
                                 LocalDateTime reportedAt) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param report       신고 엔티티
         * @param chatRoomLeft 채팅방 퇴장 여부
         * @return 응답 DTO
         */
        public static ReportResponse from(UserReport report, boolean chatRoomLeft) {
            return new ReportResponse(
                    report.getId(),
                    report.getTargetUserId(),
                    report.getReportType(),
                    report.getSeverity(),
                    report.getReportStatus(),
                    report.holdsMatching(),
                    report.getMatchId(),
                    chatRoomLeft,
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
