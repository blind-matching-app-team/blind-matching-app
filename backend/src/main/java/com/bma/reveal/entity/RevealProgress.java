package com.bma.reveal.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 매칭별 공개 단계 진행 상태({@code RV_REVEAL_PROGRESS}).
 *
 * <p>PK가 {@code MATCH_ID}이고 {@code MT_MATCH}에 대한 FK가 걸려 있으므로,
 * 실제로 존재하는 매칭에 대해서만 행을 만들 수 있다. 기존 구현은 존재하지 않는
 * matchId로 조회해도 빈 객체를 만들어 반환했는데, 이는 상대 매칭 여부를 확인하지 않고
 * 아무 ID나 조회할 수 있다는 뜻이기도 했다.</p>
 */
@Entity
@Table(name = "RV_REVEAL_PROGRESS")
@Getter
@Setter
@NoArgsConstructor
public class RevealProgress extends BaseAuditEntity {

    /** 매칭 ID(PK이자 {@code MT_MATCH}에 대한 FK). */
    @Id
    @Column(name = "MATCH_ID")
    private Long id;

    // 스키마의 UNSIGNED 타입과 맞추기 위해 columnDefinition 을 명시한다
    // (BaseAuditEntity.deleted 주석 참고).

    /** 현재 공개 단계. */
    @Column(name = "CURRENT_LEVEL", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer currentLevel = RevealPolicy.LEVEL_HIDDEN;

    /** 누적 메시지 수. */
    @Column(name = "MESSAGE_COUNT", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer messageCount = 0;

    /** 누적 대화 시간(분). */
    @Column(name = "CHAT_MINUTES", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer chatMinutes = 0;

    /** 최근 단계 상승 일시. */
    @Column(name = "LAST_LEVEL_UP_DATE")
    private LocalDateTime lastLevelUpDate;

    /** 전체 공개 도달 일시. */
    @Column(name = "FULL_REVEAL_DATE")
    private LocalDateTime fullRevealDate;

    /**
     * 매칭 성사 시점의 초기 진행 상태를 만든다.
     *
     * @param matchId 매칭 ID
     * @return 저장 대상 엔티티
     */
    public static RevealProgress startFor(Long matchId) {
        RevealProgress progress = new RevealProgress();
        progress.id = matchId;
        return progress;
    }

    /**
     * 메시지 1건 발생을 반영한다.
     *
     * @param chatMinutes 첫 메시지 이후 경과한 분
     */
    public void recordMessage(int chatMinutes) {
        this.messageCount = this.messageCount + 1;
        // 대화 시간은 되돌아가지 않도록 최댓값을 유지한다.
        this.chatMinutes = Math.max(this.chatMinutes, chatMinutes);
    }

    /**
     * 공개 단계를 올린다.
     *
     * @param newLevel 새 단계
     */
    public void levelUp(int newLevel) {
        this.currentLevel = newLevel;
        this.lastLevelUpDate = LocalDateTime.now();
        if (newLevel >= RevealPolicy.LEVEL_FULL && this.fullRevealDate == null) {
            this.fullRevealDate = LocalDateTime.now();
        }
    }
}
