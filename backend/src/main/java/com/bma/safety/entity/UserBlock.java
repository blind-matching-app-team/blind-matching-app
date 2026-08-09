package com.bma.safety.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 사용자 차단({@code SF_USER_BLOCK}).
 *
 * <p>고친 점: 기존 구현은 차단 정보를 컨트롤러의 인메모리 {@code Set}에 담고 있었다.
 * 서버를 재시작하면 사라지고 인스턴스가 둘 이상이면 아예 동작하지 않았으며,
 * 스키마에 이미 존재하던 이 테이블은 전혀 사용되지 않았다.</p>
 *
 * <p>PK가 (차단자, 대상)이므로 차단 해제 후 재차단 시에는 기존 행을 되살린다.</p>
 */
@Entity
@Table(name = "SF_USER_BLOCK")
@IdClass(UserBlock.UserBlockId.class)
@Getter
@Setter
@NoArgsConstructor
public class UserBlock extends BaseAuditEntity {

    /** 차단한 사용자(PK 일부). */
    @Id
    @Column(name = "BLOCK_USER_ID")
    private Long blockUserId;

    /** 차단당한 사용자(PK 일부). */
    @Id
    @Column(name = "TARGET_USER_ID")
    private Long targetUserId;

    /** 차단 사유(선택). */
    @Column(name = "BLOCK_REASON", length = 500)
    private String blockReason;

    /** 차단 일시. */
    @Column(name = "BLOCK_DATE", nullable = false)
    private LocalDateTime blockDate = LocalDateTime.now();

    /**
     * 새 차단 정보를 만든다.
     *
     * @param blockUserId  차단한 사용자
     * @param targetUserId 차단 대상
     * @param reason       사유(선택)
     * @return 저장 대상 엔티티
     */
    public static UserBlock of(Long blockUserId, Long targetUserId, String reason) {
        UserBlock block = new UserBlock();
        block.blockUserId = blockUserId;
        block.targetUserId = targetUserId;
        block.blockReason = reason;
        block.blockDate = LocalDateTime.now();
        return block;
    }

    /** 이전에 해제했던 차단을 다시 활성화한다. */
    public void reactivate(String reason) {
        this.blockReason = reason;
        this.blockDate = LocalDateTime.now();
        restore();
    }

    /**
     * 복합 기본키 클래스.
     *
     * <p>JPA 명세상 {@code @IdClass}는 public 기본 생성자와 {@code equals}/{@code hashCode}를
     * 갖는 Serializable 클래스여야 하므로 record를 쓸 수 없다.</p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class UserBlockId implements Serializable {

        /** 차단한 사용자. */
        private Long blockUserId;

        /** 차단당한 사용자. */
        private Long targetUserId;
    }
}
