package com.bma.safety.dto;

import com.bma.safety.entity.AdminAuditLog;
import com.bma.safety.entity.UserSanction;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * S12 관리자 신고검토 API DTO (BMA-76).
 */
public final class AdminSafetyDtos {

    private AdminSafetyDtos() {
    }

    /** 신고 유형 표시명(S11-10 라벨). */
    public static final Map<String, String> REPORT_TYPE_NAMES = Map.of(
            "ABUSE", "욕설", "FAKE", "허위프로필", "FRAUD", "사기", "SEXUAL", "부적절한 콘텐츠", "ETC", "기타");

    /**
     * 신고자 정보.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PartyInfo(Long userId, String nickname, String email, String userStatus) {
    }

    /**
     * 피신고자 정보. 누적 카운트와 현재 효력 있는 제재를 함께 싣는다(S12 판단 근거).
     *
     * @param reportCount    BMA-30 누적 카운트(자동 반영 + 관리자 유효 판정 − 감형)
     * @param activeSanction 지금 효력 있는 가장 무거운 제재. 없으면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TargetInfo(Long userId, String nickname, String email, String userStatus,
                             int reportCount, SanctionResponse activeSanction) {
    }

    /**
     * 신고 목록 항목 / 상세 공통 부분 (S12-02~05).
     *
     * @param immediateReview 중대 유형(사기·부적절한 콘텐츠)으로 즉시 검토에 올라온 건
     * @param messagePreview  근거 메시지 본문(있으면). 최대 200자
     * @param processedAt     승인/반려 일시. 미처리면 {@code null}
     * @param actionCode      승인 시 실행한 조치 NONE/WARNING/SUSPEND/BAN. 반려·미처리면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminReportSummary(Long reportId,
                                     String reportType,
                                     String reportTypeName,
                                     String severity,
                                     String status,
                                     boolean immediateReview,
                                     String description,
                                     Long matchId,
                                     Long messageId,
                                     String messagePreview,
                                     PartyInfo reporter,
                                     TargetInfo target,
                                     LocalDateTime reportedAt,
                                     LocalDateTime processedAt,
                                     Long processAdminId,
                                     String actionCode,
                                     String reviewNote) {
    }

    /**
     * 피신고자의 다른 신고 이력 항목.
     */
    public record ReportHistoryItem(Long reportId, String reportType, String severity, String status,
                                    LocalDateTime reportedAt) {
    }

    /**
     * 신고 상세 (S12 상세 패널 + S12-08 감사 로그).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminReportDetail(AdminReportSummary report,
                                    List<ReportHistoryItem> targetReportHistory,
                                    List<SanctionResponse> targetSanctions,
                                    List<AuditLogResponse> auditLogs) {
    }

    /**
     * 승인/반려 요청 (S12-06 승인 → 위험 확인모달 후 호출, S12-07 반려).
     *
     * @param decision    {@code APPROVE}(유효, 누적 반영·조치 실행) / {@code REJECT}(기각)
     * @param action      승인 시 조치 지정(직권). 생략하면 BMA-30 누적 단계(3회 경고·5회 7일 제한·7회 영구 차단)로 정한다.
     *                    {@code NONE}/{@code WARNING}/{@code SUSPEND}/{@code BAN}
     * @param suspendDays {@code SUSPEND} 일수. 기본 7
     * @param note        검토 메모(감사 로그·제재 사유에 함께 남음)
     */
    public record ReviewRequest(
            @NotBlank(message = "처리 결과는 필수입니다.")
            @Pattern(regexp = "APPROVE|REJECT", message = "처리 결과는 APPROVE 또는 REJECT 여야 합니다.")
            String decision,

            @Pattern(regexp = "NONE|WARNING|SUSPEND|BAN", message = "조치는 NONE, WARNING, SUSPEND, BAN 중 하나여야 합니다.")
            String action,

            @Min(value = 1, message = "제한 일수는 1 이상이어야 합니다.")
            @Max(value = 365, message = "제한 일수는 365 이하여야 합니다.")
            Integer suspendDays,

            @Size(max = 1000, message = "메모는 1000자를 넘을 수 없습니다.")
            String note
    ) {
        public boolean isApprove() {
            return "APPROVE".equals(decision);
        }
    }

    /**
     * 승인/반려 처리 결과.
     *
     * @param actionTaken       실행한 조치 NONE/WARNING/SUSPEND/BAN (반려면 NONE)
     * @param sanction          부과된 제재. 없으면 {@code null}
     * @param conversationEnded 신고자와의 대화(매칭·채팅방)를 "관리자에 의해 종료"로 끝냈는지
     * @param reportCountAfter  처리 후 피신고자 누적 카운트
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReviewResponse(AdminReportSummary report,
                                 String actionTaken,
                                 SanctionResponse sanction,
                                 boolean conversationEnded,
                                 int reportCountAfter) {
    }

    /**
     * 직권 제재 요청 (BMA-30 "관리자는 누적 대기 없이 직권으로 즉시 조치").
     *
     * @param type           WARNING/SUSPEND/BAN
     * @param days           SUSPEND 일수. 기본 7
     * @param reason         사유(사용자에게 노출)
     * @param sourceReportId 근거 신고(선택)
     */
    public record SanctionRequest(
            @NotBlank(message = "제재 종류는 필수입니다.")
            @Pattern(regexp = "WARNING|SUSPEND|BAN", message = "제재 종류는 WARNING, SUSPEND, BAN 중 하나여야 합니다.")
            String type,

            @Min(value = 1, message = "제한 일수는 1 이상이어야 합니다.")
            @Max(value = 365, message = "제한 일수는 365 이하여야 합니다.")
            Integer days,

            @NotBlank(message = "사유는 필수입니다.")
            @Size(max = 1000, message = "사유는 1000자를 넘을 수 없습니다.")
            String reason,

            Long sourceReportId
    ) {
    }

    /**
     * 제재 정보.
     *
     * @param active    해제되지 않았는지
     * @param effective 지금 효력이 있는지(활성이고 기간이 남음)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SanctionResponse(Long sanctionId,
                                   Long userId,
                                   String type,
                                   LocalDateTime startDate,
                                   LocalDateTime endDate,
                                   String reason,
                                   boolean active,
                                   boolean effective,
                                   Long sourceReportId) {
        public static SanctionResponse from(UserSanction s) {
            return new SanctionResponse(s.getId(), s.getUserId(), s.getSanctionType(), s.getStartDate(),
                    s.getEndDate(), s.getReason(), com.bma.common.entity.YesNo.isY(s.getActiveYn()),
                    s.isEffectiveAt(LocalDateTime.now()), s.getSourceReportId());
        }
    }

    /**
     * 감사 로그 항목 (S12-08).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AuditLogResponse(Long auditId,
                                   Long adminUserId,
                                   String adminEmail,
                                   String actionCode,
                                   Long targetUserId,
                                   Long reportId,
                                   Long sanctionId,
                                   String detail,
                                   LocalDateTime actionDate) {
        public static AuditLogResponse from(AdminAuditLog log, String adminEmail) {
            return new AuditLogResponse(log.getId(), log.getAdminUserId(), adminEmail, log.getActionCode(),
                    log.getTargetUserId(), log.getReportId(), log.getSanctionId(), log.getDetail(), log.getActionDate());
        }
    }
}
