package com.bma.matching.entity;

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
 * 블라인드 매칭 대기열({@code MT_MATCH_QUEUE}).
 */
@Entity
@Table(name = "MT_MATCH_QUEUE")
@Getter
@Setter
@NoArgsConstructor
public class MatchQueue extends BaseAuditEntity {

    /** 대기 중. */
    public static final String STATUS_WAITING = "WAITING";

    /** 매칭 처리 중. */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 매칭 완료. */
    public static final String STATUS_MATCHED = "MATCHED";

    /** 사용자가 취소함. */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 대기 시간 만료. */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 대기열 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUEUE_ID")
    private Long id;

    /** 대기 중인 사용자. */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 대기 상태. */
    @Column(name = "QUEUE_STATUS", nullable = false, length = 20)
    private String queueStatus = STATUS_WAITING;

    /** 대기열 진입 일시. */
    @Column(name = "ENTER_DATE", nullable = false)
    private LocalDateTime enterDate = LocalDateTime.now();

    /** 대기 만료 일시. 이 시각이 지나면 자동으로 만료 처리한다. */
    @Column(name = "EXPIRE_DATE")
    private LocalDateTime expireDate;

    /**
     * 대기열 항목을 만든다.
     *
     * @param userId        사용자 ID
     * @param expireMinutes 만료까지의 분
     * @return 저장 대상 엔티티
     */
    public static MatchQueue enter(Long userId, int expireMinutes) {
        MatchQueue queue = new MatchQueue();
        queue.userId = userId;
        queue.queueStatus = STATUS_WAITING;
        queue.enterDate = LocalDateTime.now();
        queue.expireDate = queue.enterDate.plusMinutes(expireMinutes);
        return queue;
    }

    /**
     * 만료 시각이 지났는지 확인한다.
     *
     * @return 만료되었으면 {@code true}
     */
    public boolean isExpired() {
        return expireDate != null && expireDate.isBefore(LocalDateTime.now());
    }

    /** 사용자가 대기를 취소했음을 기록한다. */
    public void cancel() {
        this.queueStatus = STATUS_CANCELED;
    }

    /** 대기 시간 만료를 기록한다. */
    public void expire() {
        this.queueStatus = STATUS_EXPIRED;
    }
}
