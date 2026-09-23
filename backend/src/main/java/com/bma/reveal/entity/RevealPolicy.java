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

    /** 매칭 후 최소 경과 시간(시간). BMA-19 확정 24. 정밀매칭 구독자는 스킵. */
    @Column(name = "MIN_HOURS_SINCE_MATCH", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer minHoursSinceMatch = 0;

    /** 양측 각자 최소 메시지 수. BMA-19 확정 10(합산 20). */
    @Column(name = "MIN_MESSAGES_PER_USER", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer minMessagesPerUser = 0;

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
     * 양측 각자 메시지 조건을 충족했는지 확인한다 (BMA-19: 각자 10개 이상).
     *
     * @param myMessages      내가 보낸 수
     * @param partnerMessages 상대가 보낸 수
     * @return 둘 다 기준 이상이면 {@code true}
     */
    public boolean isMessagesSatisfied(int myMessages, int partnerMessages) {
        int perUser = minMessagesPerUser == null ? 0 : minMessagesPerUser;
        return myMessages >= perUser && partnerMessages >= perUser;
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

    /**
     * 이 단계에 도달하기까지의 대화량 진행률을 백분율로 계산한다.
     *
     * <p>S5-10 / S10 의 Reveal 진행바가 쓰는 값이다. 메시지 수와 대화 시간 두 조건 중
     * <b>덜 채워진 쪽</b>을 기준으로 잰다. 둘 다 채워야 단계가 올라가므로 평균을 쓰면
     * 한쪽만 채운 상태가 "거의 다 됐다"고 보이는 착시가 생긴다.</p>
     *
     * @param messageCount 누적 메시지 수
     * @param chatMinutes  누적 대화 시간(분)
     * @return 0~100. 조건이 없는 단계(임계값 모두 0)면 100
     */
    public int progressRate(int messageCount, int chatMinutes) {
        double messageRatio = minMessageCount == null || minMessageCount == 0
                ? 1.0 : Math.min(1.0, (double) messageCount / minMessageCount);
        double minuteRatio = minChatMinutes == null || minChatMinutes == 0
                ? 1.0 : Math.min(1.0, (double) chatMinutes / minChatMinutes);
        return (int) Math.floor(Math.min(messageRatio, minuteRatio) * 100);
    }
}
