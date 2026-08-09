package com.bma.safety.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자 및 채팅 신고({@code SF_USER_REPORT}).
 */
@Entity
@Table(name = "SF_USER_REPORT")
@Getter
@Setter
@NoArgsConstructor
public class UserReport extends BaseAuditEntity {

    /** 처리 상태: 접수됨. */
    public static final String STATUS_RECEIVED = "RECEIVED";

    /** 신고 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REPORT_ID")
    private Long id;

    /** 신고자 ID. */
    @Column(name = "REPORT_USER_ID", nullable = false)
    private Long reportUserId;

    /** 피신고자 ID. */
    @Column(name = "TARGET_USER_ID", nullable = false)
    private Long targetUserId;

    /** 관련 매칭 ID(선택). */
    @Column(name = "MATCH_ID")
    private Long matchId;

    /** 관련 메시지 ID(선택). */
    @Column(name = "MESSAGE_ID")
    private Long messageId;

    /** 신고 유형(ABUSE/FAKE/FRAUD/SEXUAL/ETC). */
    @Column(name = "REPORT_TYPE", nullable = false, length = 30)
    private String reportType;

    /** 상세 신고 내용. */
    @Column(name = "REPORT_CONTENT", length = 2000)
    private String reportContent;

    /** 처리 상태. */
    @Column(name = "REPORT_STATUS", nullable = false)
    private String reportStatus = STATUS_RECEIVED;

    /**
     * 새 신고를 만든다.
     *
     * @param reportUserId 신고자
     * @param targetUserId 피신고자
     * @param reportType   신고 유형
     * @param content      상세 내용
     * @param matchId      관련 매칭(선택)
     * @param messageId    관련 메시지(선택)
     * @return 저장 대상 엔티티
     */
    public static UserReport of(Long reportUserId, Long targetUserId, String reportType,
                                String content, Long matchId, Long messageId) {
        UserReport report = new UserReport();
        report.reportUserId = reportUserId;
        report.targetUserId = targetUserId;
        report.reportType = reportType;
        report.reportContent = content;
        report.matchId = matchId;
        report.messageId = messageId;
        return report;
    }
}
