package com.bma.reveal.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5-10 / S10 Reveal 진행바가 쓰는 진행률 계산을 고정한다.
 *
 * <p>메시지 수와 대화 시간 두 조건을 모두 채워야 단계가 올라가므로, 진행률은 둘 중
 * 덜 채워진 쪽이어야 한다. 평균을 쓰면 한쪽만 채운 상태가 거의 다 된 것처럼 보인다.</p>
 */
class RevealPolicyProgressTest {

    private RevealPolicy policy(int minMessages, int minMinutes) {
        RevealPolicy policy = new RevealPolicy();
        policy.setRevealLevel(1);
        policy.setMinMessageCount(minMessages);
        policy.setMinChatMinutes(minMinutes);
        return policy;
    }

    @Test
    @DisplayName("둘 중 덜 채워진 조건이 진행률이다")
    void usesTheLaggingCondition() {
        // 메시지 20개 중 10개(50%), 시간 10분 중 2분(20%) → 20%
        assertThat(policy(20, 10).progressRate(10, 2)).isEqualTo(20);
        // 메시지 20개 중 20개(100%), 시간 10분 중 5분(50%) → 50%
        assertThat(policy(20, 10).progressRate(20, 5)).isEqualTo(50);
    }

    @Test
    @DisplayName("조건을 넘겨도 100 을 넘지 않는다")
    void capsAt100() {
        assertThat(policy(20, 10).progressRate(200, 100)).isEqualTo(100);
    }

    @Test
    @DisplayName("아직 대화가 없으면 0 이다")
    void zeroWhenNoActivity() {
        assertThat(policy(20, 10).progressRate(0, 0)).isEqualTo(0);
    }

    @Test
    @DisplayName("임계값이 0 인 조건은 이미 충족한 것으로 본다")
    void zeroThresholdCountsAsSatisfied() {
        assertThat(policy(0, 0).progressRate(0, 0)).isEqualTo(100);
        assertThat(policy(20, 0).progressRate(5, 0)).isEqualTo(25);
    }
}
