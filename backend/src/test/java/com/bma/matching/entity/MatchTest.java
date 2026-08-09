package com.bma.matching.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Match} 단위 테스트.
 *
 * <p>DB의 {@code CK_MT_MATCH_USER_ORDER CHECK (USER1_ID < USER2_ID)} 제약과
 * {@code UK_MT_MATCH_USERS} 유니크 제약을 애플리케이션이 항상 만족시키는지 확인한다.
 * 이 규칙이 깨지면 (A,B)와 (B,A)가 중복 매칭으로 들어가거나 삽입이 실패한다.</p>
 */
class MatchTest {

    @Test
    @DisplayName("참여자 순서와 무관하게 항상 작은 ID가 user1Id에 들어간다")
    void between_ordersUserIds() {
        Match forward = Match.between(10L, 3L, Match.TYPE_LIKE);
        Match reverse = Match.between(3L, 10L, Match.TYPE_LIKE);

        assertThat(forward.getUser1Id()).isEqualTo(3L);
        assertThat(forward.getUser2Id()).isEqualTo(10L);
        // 어느 방향으로 호출해도 같은 조합이 되어야 중복 매칭이 생기지 않는다.
        assertThat(reverse.getUser1Id()).isEqualTo(forward.getUser1Id());
        assertThat(reverse.getUser2Id()).isEqualTo(forward.getUser2Id());
    }

    @Test
    @DisplayName("상대방 ID를 정확히 찾는다")
    void partnerOf_returnsOtherParticipant() {
        Match match = Match.between(3L, 10L, Match.TYPE_LIKE);

        assertThat(match.partnerOf(3L)).isEqualTo(10L);
        assertThat(match.partnerOf(10L)).isEqualTo(3L);
    }

    @Test
    @DisplayName("참여자가 아닌 사용자로 상대를 조회하면 예외가 발생한다")
    void partnerOf_rejectsNonParticipant() {
        Match match = Match.between(3L, 10L, Match.TYPE_LIKE);

        assertThatThrownBy(() -> match.partnerOf(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("참여자 여부를 판별한다")
    void hasParticipant() {
        Match match = Match.between(3L, 10L, Match.TYPE_LIKE);

        assertThat(match.hasParticipant(3L)).isTrue();
        assertThat(match.hasParticipant(10L)).isTrue();
        assertThat(match.hasParticipant(99L)).isFalse();
    }

    @Test
    @DisplayName("종료 처리하면 진행 중 상태가 해제된다")
    void terminate_marksInactive() {
        Match match = Match.between(3L, 10L, Match.TYPE_LIKE);
        assertThat(match.isActive()).isTrue();

        match.terminate(Match.STATUS_BLOCKED, 3L, "BLOCK");

        assertThat(match.isActive()).isFalse();
        assertThat(match.getMatchStatus()).isEqualTo(Match.STATUS_BLOCKED);
        assertThat(match.getEndUserId()).isEqualTo(3L);
        assertThat(match.getEndDate()).isNotNull();
    }
}
