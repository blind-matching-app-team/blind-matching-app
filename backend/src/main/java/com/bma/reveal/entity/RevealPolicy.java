package com.bma.reveal.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 3단계 블라인드 해제 정책({@code RV_REVEAL_POLICY}).
 *
 * <p>스키마에 이미 기본 데이터(0/1/2단계)가 들어 있지만 기존 코드는 이 테이블을
 * 전혀 읽지 않아 Reveal 기능이 사실상 동작하지 않았다. 이제 단계 상승 조건을
 * 코드에 하드코딩하지 않고 이 테이블에서 읽어 판정한다.</p>
 */
@Entity
@Table(name = "RV_REVEAL_POLICY")
@Getter
@Setter
@NoArgsConstructor
public class RevealPolicy extends BaseAuditEntity {

    /** 단계 0: 미공개(실루엣). */
    public static final int LEVEL_HIDDEN = 0;

    /** 단계 1: 실루엣 및 부분 공개(블러). */
    public static final int LEVEL_PARTIAL = 1;

    /** 단계 2: 전체 공개. */
    public static final int LEVEL_FULL = 2;

    // 스키마의 UNSIGNED / CHAR(1) 타입과 맞추기 위해 columnDefinition 을 명시한다
    // (BaseAuditEntity.deleted 주석 참고).

    /** 공개 단계(PK). */
    @Id
    @Column(name = "REVEAL_LEVEL", columnDefinition = "TINYINT UNSIGNED")
    private Integer revealLevel;

    /** 단계명. */
    @Column(name = "REVEAL_NAME", nullable = false, length = 100)
    private String revealName;

    /** 이 단계로 올라가는 데 필요한 누적 메시지 수. */
    @Column(name = "MIN_MESSAGE_COUNT", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer minMessageCount = 0;

    /** 이 단계로 올라가는 데 필요한 누적 대화 시간(분). */
    @Column(name = "MIN_CHAT_MINUTES", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer minChatMinutes = 0;

    /** 양쪽 모두의 동의가 필요한지 여부. */
    @Column(name = "MUTUAL_CONSENT_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String mutualConsentYn = YesNo.N;

    /** 사용 여부. */
    @Column(name = "USE_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String useYn = YesNo.Y;

    /**
     * 상호 동의가 필요한 단계인지 확인한다.
     *
     * @return 필요하면 {@code true}
     */
    public boolean requiresMutualConsent() {
        return YesNo.isY(mutualConsentYn);
    }

    /**
     * 대화량 조건을 충족했는지 확인한다.
     *
     * @param messageCount 누적 메시지 수
     * @param chatMinutes  누적 대화 시간(분)
     * @return 충족했으면 {@code true}
     */
    public boolean isActivitySatisfied(int messageCount, int chatMinutes) {
        return messageCount >= minMessageCount && chatMinutes >= minChatMinutes;
    }
}
