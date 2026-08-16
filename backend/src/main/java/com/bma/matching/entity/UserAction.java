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
import java.util.Set;

/**
 * 추천 상대에 대한 좋아요·패스({@code MT_USER_ACTION}).
 *
 * <p><b>중요</b>: 기존 엔티티는 컬럼명을 {@code ACTOR_USER_ID}/{@code TARGET_USER_ID}로
 * 매핑했지만 실제 스키마는 {@code FROM_USER_ID}/{@code TO_USER_ID}다.
 * {@code ddl-auto: validate} 때문에 이 불일치만으로 기동이 실패했다.</p>
 *
 * <p>{@code UK_MT_USER_ACTION(FROM_USER_ID, TO_USER_ID)} 유니크 제약이 있어
 * 같은 상대에게 두 번 액션하면 삽입이 실패한다. 서비스는 기존 행을 갱신하는 방식으로 처리한다.</p>
 */
@Entity
@Table(name = "MT_USER_ACTION")
@Getter
@Setter
@NoArgsConstructor
public class UserAction extends BaseAuditEntity {

    /** 액션: 좋아요. */
    public static final String TYPE_LIKE = "LIKE";

    /** 액션: 슈퍼 좋아요. */
    public static final String TYPE_SUPER_LIKE = "SUPER_LIKE";

    /** 액션: 패스. */
    public static final String TYPE_PASS = "PASS";

    /** 허용되는 액션 유형. 이 외의 값은 요청 단계에서 거른다. */
    public static final Set<String> ALLOWED_TYPES = Set.of(TYPE_LIKE, TYPE_SUPER_LIKE, TYPE_PASS);

    /** 호감 표시로 간주하는 액션 유형. 양쪽이 모두 이 상태면 매칭이 성사된다. */
    private static final Set<String> POSITIVE_TYPES = Set.of(TYPE_LIKE, TYPE_SUPER_LIKE);

    /** 행동 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACTION_ID")
    private Long id;

    /** 행동을 한 사용자. */
    @Column(name = "FROM_USER_ID", nullable = false)
    private Long fromUserId;

    /** 행동 대상 사용자. */
    @Column(name = "TO_USER_ID", nullable = false)
    private Long toUserId;

    /** 액션 유형(LIKE/SUPER_LIKE/PASS). */
    @Column(name = "ACTION_TYPE", nullable = false, length = 20)
    private String actionType;

    /** 호감 메시지(선택). */
    @Column(name = "MESSAGE", length = 500)
    private String message;

    /** 행동 일시. */
    @Column(name = "ACTION_DATE", nullable = false)
    private LocalDateTime actionDate = LocalDateTime.now();

    /**
     * 새 액션을 만든다.
     *
     * @param fromUserId 행동 주체
     * @param toUserId   대상
     * @param actionType 액션 유형
     * @param message    호감 메시지(선택)
     * @return 저장 대상 엔티티
     */
    public static UserAction of(Long fromUserId, Long toUserId, String actionType, String message) {
        UserAction action = new UserAction();
        action.fromUserId = fromUserId;
        action.toUserId = toUserId;
        action.actionType = actionType;
        action.message = message;
        action.actionDate = LocalDateTime.now();
        return action;
    }

    /**
     * 기존 액션을 새 값으로 갱신한다(유니크 제약 때문에 재삽입할 수 없다).
     *
     * @param actionType 새 액션 유형
     * @param message    새 메시지
     */
    public void update(String actionType, String message) {
        this.actionType = actionType;
        this.message = message;
        this.actionDate = LocalDateTime.now();
        restore();
    }

    /**
     * 호감 표시인지 확인한다.
     *
     * @return LIKE 또는 SUPER_LIKE이면 {@code true}
     */
    public boolean isPositive() {
        return POSITIVE_TYPES.contains(actionType) && !isDeleted();
    }
}
