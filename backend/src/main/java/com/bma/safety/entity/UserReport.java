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
import java.util.Set;

/**
 * 사용자 및 채팅 신고({@code SF_USER_REPORT}).
 *
 * <p>BMA-30 확정 정책</p>
 * <ul>
 *   <li>유형 5종: ABUSE(욕설)/FAKE(허위프로필)/ETC(기타)는 일반, FRAUD(사기)/SEXUAL(부적절한 콘텐츠)는 중대.</li>
 *   <li>중대 유형은 신고 1회로 즉시 관리자 검토({@link #STATUS_PENDING_REVIEW}). 검토 대기 중인 사용자는 새 매칭에 들어갈 수 없다.</li>
 *   <li>일반 유형은 누적 1~2회 자동 반영({@link #STATUS_COUNTED}), 3회째부터는 관리자 검토를 거쳐야 카운트된다.</li>
 * </ul>
 */
@Entity
@Table(name = "SF_USER_REPORT")
@Getter
@Setter
@NoArgsConstructor
public class UserReport extends BaseAuditEntity {

    /** (구) 접수됨. V13 부터 {@link #STATUS_COUNTED} 로 통일했다. */
    public static final String STATUS_RECEIVED = "RECEIVED";
    /** 자동 반영(누적 1~2회). 관리자 조치 없음. */
    public static final String STATUS_COUNTED = "COUNTED";
    /** 관리자 검토 대기(누적 3회째부터, 또는 중대 유형). */
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    /** 관리자가 유효로 판정. */
    public static final String STATUS_RESOLVED = "RESOLVED";
    /** 관리자가 기각. */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 심각도: 누적 횟수 기반. */
    public static final String SEVERITY_NORMAL = "NORMAL";
    /** 심각도: 1회로 즉시 관리자 검토. */
    public static final String SEVERITY_SEVERE = "SEVERE";

    /** 중대 유형(사기, 부적절한 콘텐츠). */
    public static final Set<String> SEVERE_TYPES = Set.of("FRAUD", "SEXUAL");
    /** 누적 카운트에 반영된 상태. 3회째부터 검토를 요구할 때 이 상태들을 센다. */
    public static final Set<String> COUNTED_STATUSES = Set.of(STATUS_RECEIVED, STATUS_COUNTED, STATUS_RESOLVED);
    /** 자동 반영 상한. 이 수를 넘기는 신고부터 관리자 검토 대상이다. */
    public static final int AUTO_COUNT_LIMIT = 2;

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

    /** 관련 매칭 ID(선택). 같은 매칭에 대한 재신고를 막는 기준. */
    @Column(name = "MATCH_ID")
    private Long matchId;

    /** 관련 메시지 ID(선택). */
    @Column(name = "MESSAGE_ID")
    private Long messageId;

    /** 신고 유형(ABUSE/FAKE/FRAUD/SEXUAL/ETC). */
    @Column(name = "REPORT_TYPE", nullable = false, length = 30)
    private String reportType;

    /** 심각도(NORMAL/SEVERE). */
    @Column(name = "SEVERITY", nullable = false, length = 10)
    private String severity = SEVERITY_NORMAL;

    /** 상세 신고 내용. */
    @Column(name = "REPORT_CONTENT", length = 2000)
    private String reportContent;

    /** 처리 상태. */
    @Column(name = "REPORT_STATUS", nullable = false)
    private String reportStatus = STATUS_COUNTED;

    /** 승인 시 실행한 조치(NONE/WARNING/SUSPEND/BAN). 반려면 {@code null}. */
    @Column(name = "ACTION_CODE", length = 30)
    private String actionCode;

    /** 처리 관리자. */
    @Column(name = "PROCESS_USER_ID")
    private Long processUserId;

    /** 처리 일시. */
    @Column(name = "PROCESS_DATE")
    private LocalDateTime processDate;

    /** 관리자 검토 메모. */
    @Column(name = "REVIEW_NOTE", length = 1000)
    private String reviewNote;

    /**
     * 유형이 중대(즉시 관리자 검토) 유형인지 판정한다.
     *
     * @param reportType 신고 유형
     * @return 사기·부적절한 콘텐츠면 {@code true}
     */
    public static boolean isSevereType(String reportType) {
        return SEVERE_TYPES.contains(reportType);
    }

    /**
     * BMA-30 정책으로 새 신고의 상태를 정한다.
     *
     * @param reportType    신고 유형
     * @param countedBefore 이 신고 이전에 피신고자에게 이미 반영된 누적 횟수
     * @return 자동 반영이면 {@link #STATUS_COUNTED}, 검토 대상이면 {@link #STATUS_PENDING_REVIEW}
     */
    public static String decideStatus(String reportType, long countedBefore) {
        if (isSevereType(reportType) || countedBefore >= AUTO_COUNT_LIMIT) {
            return STATUS_PENDING_REVIEW;
        }
        return STATUS_COUNTED;
    }

    /**
     * 새 신고를 만든다. 심각도와 상태는 정책에 따라 자동으로 정해진다.
     *
     * @param reportUserId  신고자
     * @param targetUserId  피신고자
     * @param reportType    신고 유형
     * @param content       상세 내용
     * @param matchId       관련 매칭(선택)
     * @param messageId     관련 메시지(선택)
     * @param countedBefore 피신고자의 기존 누적 횟수
     * @return 저장 대상 엔티티
     */
    public static UserReport of(Long reportUserId, Long targetUserId, String reportType,
                                String content, Long matchId, Long messageId, long countedBefore) {
        UserReport report = new UserReport();
        report.reportUserId = reportUserId;
        report.targetUserId = targetUserId;
        report.reportType = reportType;
        report.severity = isSevereType(reportType) ? SEVERITY_SEVERE : SEVERITY_NORMAL;
        report.reportContent = content;
        report.matchId = matchId;
        report.messageId = messageId;
        report.reportStatus = decideStatus(reportType, countedBefore);
        return report;
    }

    /**
     * 관리자 검토를 기다리는 신고인지 확인한다.
     *
     * @return 검토 대기면 {@code true}
     */
    public boolean isPendingReview() {
        return STATUS_PENDING_REVIEW.equals(reportStatus);
    }

    /**
     * 즉시검토(중대 유형) 대기 신고인지 확인한다. 이 신고가 있는 사용자는 새 매칭에 들어갈 수 없다.
     *
     * @return 중대 유형이고 검토 대기면 {@code true}
     */
    public boolean holdsMatching() {
        return SEVERITY_SEVERE.equals(severity) && isPendingReview();
    }

    /**
     * 관리자가 유효로 판정한다(S12-06 승인).
     *
     * @param adminUserId 관리자
     * @param actionCode  실행한 조치
     * @param note        메모
     */
    public void approve(Long adminUserId, String actionCode, String note) {
        this.reportStatus = STATUS_RESOLVED;
        this.actionCode = actionCode;
        this.processUserId = adminUserId;
        this.processDate = LocalDateTime.now();
        this.reviewNote = note;
    }

    /**
     * 관리자가 기각한다(S12-07 반려). 누적에 반영되지 않고 즉시검토 대기도 풀린다.
     *
     * @param adminUserId 관리자
     * @param note        메모
     */
    public void reject(Long adminUserId, String note) {
        this.reportStatus = STATUS_REJECTED;
        this.actionCode = null;
        this.processUserId = adminUserId;
        this.processDate = LocalDateTime.now();
        this.reviewNote = note;
    }

    /**
     * 처리(승인/반려)가 끝났는지 확인한다.
     *
     * @return RESOLVED 또는 REJECTED 면 {@code true}
     */
    public boolean isProcessed() {
        return STATUS_RESOLVED.equals(reportStatus) || STATUS_REJECTED.equals(reportStatus);
    }
}
