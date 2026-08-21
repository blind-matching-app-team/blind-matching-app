package com.bma.safety.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
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
 * 신고 누적 등에 따른 사용자 제재 이력({@code SF_USER_SANCTION}).
 *
 * <p>스키마에는 있었으나 엔티티가 없어 로그인 실패 응답에 정지 사유와 해제 시각을
 * 실을 수 없었다. 이용정지 화면(S1-18~21)의 데이터 출처가 이 테이블이다.</p>
 *
 * <p>제재 종류 중 로그인을 막는 것은 {@link #TYPE_SUSPEND} 와 {@link #TYPE_BAN} 뿐이다.
 * 경고나 기능 제한은 로그인 자체를 막지 않는다.</p>
 */
@Entity
@Table(name = "SF_USER_SANCTION")
@Getter
@Setter
@NoArgsConstructor
public class UserSanction extends BaseAuditEntity {

    /** 경고. 로그인을 막지 않는다. */
    public static final String TYPE_WARNING = "WARNING";

    /** 채팅 기능 제한. 로그인을 막지 않는다. */
    public static final String TYPE_CHAT_LIMIT = "CHAT_LIMIT";

    /** 매칭 기능 제한. 로그인을 막지 않는다. */
    public static final String TYPE_MATCH_LIMIT = "MATCH_LIMIT";

    /** 기간제 이용 정지. 로그인을 막는다. */
    public static final String TYPE_SUSPEND = "SUSPEND";

    /** 영구 이용 정지. 로그인을 막는다. */
    public static final String TYPE_BAN = "BAN";

    /** 제재 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SANCTION_ID")
    private Long id;

    /** 제재 대상 사용자 ID. */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 제재 종류. */
    @Column(name = "SANCTION_TYPE", nullable = false, length = 30)
    private String sanctionType;

    /** 제재 시작 일시. */
    @Column(name = "START_DATE", nullable = false)
    private LocalDateTime startDate;

    /** 제재 종료 일시. 영구 제재이면 {@code null}. */
    @Column(name = "END_DATE")
    private LocalDateTime endDate;

    /** 제재 사유. 사용자에게 그대로 노출된다. */
    @Column(name = "REASON", nullable = false, length = 1000)
    private String reason;

    /** 근거가 된 신고 ID. */
    @Column(name = "SOURCE_REPORT_ID")
    private Long sourceReportId;

    /** 활성 여부. 해제된 제재는 'N'. */
    @Column(name = "ACTIVE_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String activeYn = YesNo.Y;

    /**
     * 로그인을 막는 제재인지 확인한다.
     *
     * @return 정지 또는 영구정지이면 {@code true}
     */
    public boolean blocksLogin() {
        return TYPE_SUSPEND.equals(sanctionType) || TYPE_BAN.equals(sanctionType);
    }

    /**
     * 영구 제재인지 확인한다.
     *
     * <p>{@link #TYPE_BAN} 이거나, 정지인데 종료 일시가 없으면 영구로 본다.
     * 종료 일시 없는 정지를 기간제로 취급하면 해제 시각이 {@code null} 인 채로
     * 프론트에 "언젠가 풀린다"고 잘못 안내하게 된다.</p>
     *
     * @return 영구 제재이면 {@code true}
     */
    public boolean isPermanent() {
        return TYPE_BAN.equals(sanctionType) || endDate == null;
    }

    /**
     * 지금 시점에 효력이 있는 제재인지 확인한다.
     *
     * @param now 기준 시각
     * @return 활성 상태이고 아직 종료되지 않았으면 {@code true}
     */
    public boolean isEffectiveAt(LocalDateTime now) {
        if (!YesNo.isY(activeYn) || isDeleted()) {
            return false;
        }
        return endDate == null || endDate.isAfter(now);
    }
}
