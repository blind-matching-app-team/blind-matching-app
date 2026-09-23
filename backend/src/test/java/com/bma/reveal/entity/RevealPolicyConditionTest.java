package com.bma.reveal.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BMA-19 확정 조건(각자 10개 이상, 합산 20)이 정책 엔티티에서 그대로 판정되는지 고정한다.
 */
class RevealPolicyConditionTest {

    private static RevealPolicy policy(int perUser, int total, int hours) {
        RevealPolicy p = new RevealPolicy();
        p.setRevealLevel(1);
        p.setRevealName("부분 공개");
        p.setMinMessagesPerUser(perUser);
        p.setMinMessageCount(total);
        p.setMinHoursSinceMatch(hours);
        p.setMutualConsentYn("Y");
        return p;
    }

    @Test
    @DisplayName("각자 10개: 한쪽만 많이 보내면 미충족, 둘 다 10개 이상이면 충족")
    void perUserRule() {
        RevealPolicy p = policy(10, 20, 24);
        assertThat(p.isMessagesSatisfied(20, 0)).isFalse();
        assertThat(p.isMessagesSatisfied(9, 11)).isFalse();
        assertThat(p.isMessagesSatisfied(10, 10)).isTrue();
        assertThat(p.isMessagesSatisfied(30, 12)).isTrue();
    }

    @Test
    @DisplayName("합산 기준(20)과 시간 기준(24)은 응답 표시용 값으로 그대로 노출된다")
    void thresholdsExposed() {
        RevealPolicy p = policy(10, 20, 24);
        assertThat(p.getMinMessageCount()).isEqualTo(20);
        assertThat(p.getMinHoursSinceMatch()).isEqualTo(24);
        assertThat(p.requiresMutualConsent()).isTrue();
    }

    @Test
    @DisplayName("조건 값이 비어 있으면(0) 항상 충족으로 본다")
    void zeroThresholdMeansNoCondition() {
        RevealPolicy p = policy(0, 0, 0);
        assertThat(p.isMessagesSatisfied(0, 0)).isTrue();
    }
}
