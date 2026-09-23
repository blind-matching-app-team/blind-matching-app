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

import java.time.LocalDateTime;

/**
 * 관리자 조치 감사 로그({@code SF_ADMIN_AUDIT_LOG}, BMA-30 "누가/언제/무슨 근거/무슨 조치").
 *
 * <p>S12-08 감사 로그 표시의 데이터 출처. 행은 절대 갱신·삭제하지 않는다.</p>
 */
@Entity
@Table(name = "SF_ADMIN_AUDIT_LOG")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuditLog extends BaseAuditEntity {

    public static final String ACTION_REPORT_APPROVE = "REPORT_APPROVE";
    public static final String ACTION_REPORT_REJECT = "REPORT_REJECT";
    public static final String ACTION_SANCTION_WARNING = "SANCTION_WARNING";
    public static final String ACTION_SANCTION_SUSPEND = "SANCTION_SUSPEND";
    public static final String ACTION_SANCTION_BAN = "SANCTION_BAN";
    public static final String ACTION_SANCTION_LIFT = "SANCTION_LIFT";
    public static final String ACTION_CONVERSATION_END = "CONVERSATION_END";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long id;

    /** 행위 관리자. */
    @Column(name = "ADMIN_USER_ID", nullable = false)
    private Long adminUserId;

    /** 행위 종류. */
    @Column(name = "ACTION_CODE", nullable = false, length = 30)
    private String actionCode;

    /** 조치 대상 사용자. */
    @Column(name = "TARGET_USER_ID")
    private Long targetUserId;

    /** 근거 신고. */
    @Column(name = "REPORT_ID")
    private Long reportId;

    /** 부과/해제된 제재. */
    @Column(name = "SANCTION_ID")
    private Long sanctionId;

    /** 근거·메모. */
    @Column(name = "DETAIL", length = 1000)
    private String detail;

    /** 행위 일시. */
    @Column(name = "ACTION_DATE", nullable = false)
    private LocalDateTime actionDate = LocalDateTime.now();

    /**
     * 감사 로그 한 줄을 만든다.
     *
     * @param adminUserId  관리자
     * @param actionCode   행위
     * @param targetUserId 대상 사용자(선택)
     * @param reportId     근거 신고(선택)
     * @param sanctionId   제재(선택)
     * @param detail       근거·메모
     * @return 저장 대상 엔티티
     */
    public static AdminAuditLog of(Long adminUserId, String actionCode, Long targetUserId,
                                   Long reportId, Long sanctionId, String detail) {
        AdminAuditLog log = new AdminAuditLog();
        log.adminUserId = adminUserId;
        log.actionCode = actionCode;
        log.targetUserId = targetUserId;
        log.reportId = reportId;
        log.sanctionId = sanctionId;
        log.detail = detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        log.actionDate = LocalDateTime.now();
        return log;
    }
}
