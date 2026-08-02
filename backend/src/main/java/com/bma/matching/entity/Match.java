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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상호 호감 또는 큐 기반으로 성사된 매칭({@code MT_MATCH}).
 *
 * <p><b>중요</b>: 기존 엔티티는 컬럼명을 {@code USER_ID_1}/{@code USER_ID_2}로 매핑했지만
 * 실제 스키마는 {@code USER1_ID}/{@code USER2_ID}다. {@code ddl-auto: validate} 설정 때문에
 * 이 불일치만으로 애플리케이션이 기동조차 되지 않았다.</p>
 *
 * <p>또한 스키마에 {@code CK_MT_MATCH_USER_ORDER CHECK (USER1_ID < USER2_ID)} 제약이 있으므로
 * 항상 작은 ID를 {@code user1Id}에 넣어야 한다. {@link #between(Long, Long, String)}이 이를 보장한다.</p>
 */
@Entity
@Table(name = "MT_MATCH")
@Getter
@Setter
@NoArgsConstructor
public class Match extends BaseAuditEntity {

    /** 매칭 상태: 진행 중. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 매칭 상태: 해제됨. */
    public static final String STATUS_UNMATCHED = "UNMATCHED";

    /** 매칭 상태: 차단으로 종료. */
    public static final String STATUS_BLOCKED = "BLOCKED";

    /** 매칭 유형: 상호 좋아요. */
    public static final String TYPE_LIKE = "LIKE";

    /** 매칭 유형: 대기열 기반. */
    public static final String TYPE_QUEUE = "QUEUE";

    /** 매칭 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MATCH_ID")
    private Long id;

    /** 두 참여자 중 ID가 작은 쪽. */
    @Column(name = "USER1_ID", nullable = false)
    private Long user1Id;

    /** 두 참여자 중 ID가 큰 쪽. */
    @Column(name = "USER2_ID", nullable = false)
    private Long user2Id;

    /** 매칭 유형(LIKE/QUEUE/MANUAL). */
    @Column(name = "MATCH_TYPE", nullable = false, length = 20)
    private String matchType = TYPE_LIKE;

    /** 매칭 상태. */
    @Column(name = "MATCH_STATUS", nullable = false, length = 20)
    private String matchStatus = STATUS_ACTIVE;

    /** 성사 시점의 매칭 점수. */
    @Column(name = "MATCH_SCORE")
    private BigDecimal matchScore;

    /** 매칭 성사 일시. */
    @Column(name = "MATCH_DATE", nullable = false)
    private LocalDateTime matchDate = LocalDateTime.now();

    /** 매칭 종료 일시. */
    @Column(name = "END_DATE")
    private LocalDateTime endDate;

    /** 종료를 수행한 사용자. */
    @Column(name = "END_USER_ID")
    private Long endUserId;

    /** 종료 사유 코드. */
    @Column(name = "END_REASON_CODE", length = 30)
    private String endReasonCode;

    /**
     * 두 사용자 사이의 매칭을 만든다.
     *
     * <p>DB의 {@code USER1_ID < USER2_ID} 체크 제약과 {@code UK_MT_MATCH_USERS} 유니크 제약을
     * 만족시키기 위해 ID를 오름차순으로 정렬해서 넣는다. 이렇게 해야 (A,B)와 (B,A)가
     * 중복 매칭으로 생성되는 것도 함께 막힌다.</p>
     *
     * @param userIdA   참여자 A
     * @param userIdB   참여자 B
     * @param matchType 매칭 유형
     * @return 저장 대상 엔티티
     */
    public static Match between(Long userIdA, Long userIdB, String matchType) {
        Match match = new Match();
        match.user1Id = Math.min(userIdA, userIdB);
        match.user2Id = Math.max(userIdA, userIdB);
        match.matchType = matchType;
        match.matchStatus = STATUS_ACTIVE;
        match.matchDate = LocalDateTime.now();
        return match;
    }

    /**
     * 해당 사용자가 이 매칭의 참여자인지 확인한다.
     *
     * @param userId 확인할 사용자 ID
     * @return 참여자이면 {@code true}
     */
    public boolean hasParticipant(Long userId) {
        return user1Id.equals(userId) || user2Id.equals(userId);
    }

    /**
     * 상대방 ID를 반환한다.
     *
     * @param userId 나의 ID
     * @return 상대방 ID
     * @throws IllegalArgumentException 참여자가 아닌 ID를 넘긴 경우
     */
    public Long partnerOf(Long userId) {
        if (user1Id.equals(userId)) {
            return user2Id;
        }
        if (user2Id.equals(userId)) {
            return user1Id;
        }
        throw new IllegalArgumentException("매칭 참여자가 아닙니다: userId=" + userId);
    }

    /**
     * 진행 중인 매칭인지 확인한다.
     *
     * @return 진행 중이면 {@code true}
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(matchStatus) && !isDeleted();
    }

    /**
     * 매칭을 종료한다.
     *
     * @param status     종료 상태(UNMATCHED/BLOCKED)
     * @param endUserId  종료를 수행한 사용자
     * @param reasonCode 종료 사유 코드
     */
    public void terminate(String status, Long endUserId, String reasonCode) {
        this.matchStatus = status;
        this.endUserId = endUserId;
        this.endReasonCode = reasonCode;
        this.endDate = LocalDateTime.now();
    }
}
