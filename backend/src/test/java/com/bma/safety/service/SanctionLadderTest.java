package com.bma.safety.service;

import com.bma.safety.entity.UserSanction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BMA-30 조치 단계(3회 경고·5회 7일 제한·7회 영구 차단)와 감형(첫 제한 종료 시 2, 1회 한정) 판정을 고정한다.
 */
class SanctionLadderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 12, 0);

    private static UserSanction suspend(LocalDateTime start, LocalDateTime end) {
        UserSanction s = UserSanction.of(1L, UserSanction.TYPE_SUSPEND, end, "test", null);
        s.setStartDate(start);
        return s;
    }

    @Test
    @DisplayName("임계값 도달: 3회 경고, 5회 7일 제한, 7회 이상 영구 차단, 그 외 조치 없음")
    void ladderAtThresholds() {
        assertThat(SanctionService.decideLadderAction(1, false, false)).isEqualTo(SanctionService.ACTION_NONE);
        assertThat(SanctionService.decideLadderAction(2, false, false)).isEqualTo(SanctionService.ACTION_NONE);
        assertThat(SanctionService.decideLadderAction(3, false, false)).isEqualTo(UserSanction.TYPE_WARNING);
        assertThat(SanctionService.decideLadderAction(4, true, false)).isEqualTo(SanctionService.ACTION_NONE);
        assertThat(SanctionService.decideLadderAction(5, true, false)).isEqualTo(UserSanction.TYPE_SUSPEND);
        assertThat(SanctionService.decideLadderAction(6, true, true)).isEqualTo(SanctionService.ACTION_NONE);
        assertThat(SanctionService.decideLadderAction(7, true, true)).isEqualTo(UserSanction.TYPE_BAN);
        assertThat(SanctionService.decideLadderAction(9, true, true)).isEqualTo(UserSanction.TYPE_BAN);
    }

    @Test
    @DisplayName("감형 뒤 다시 5회가 되면 두 번째 제한이 나간다(count==5)")
    void secondSuspensionAtFiveAgain() {
        assertThat(SanctionService.decideLadderAction(5, true, true)).isEqualTo(UserSanction.TYPE_SUSPEND);
    }

    @Test
    @DisplayName("임계값을 건너뛴 경우 아직 받은 적 없는 가장 높은 단계를 실행한다")
    void skippedThresholds() {
        assertThat(SanctionService.decideLadderAction(4, false, false)).isEqualTo(UserSanction.TYPE_WARNING);
        assertThat(SanctionService.decideLadderAction(6, true, false)).isEqualTo(UserSanction.TYPE_SUSPEND);
        assertThat(SanctionService.decideLadderAction(6, false, false)).isEqualTo(UserSanction.TYPE_SUSPEND);
    }

    @Test
    @DisplayName("감형: 첫 7일 제한이 끝났을 때만 2, 진행 중이면 0, 두 번째 제한 종료는 감형 없음")
    void reductionOnlyForFirstEndedSuspension() {
        assertThat(SanctionService.reductionFor(List.of(), NOW)).isZero();
        assertThat(SanctionService.reductionFor(
                List.of(suspend(NOW.minusDays(1), NOW.plusDays(6))), NOW)).isZero();
        assertThat(SanctionService.reductionFor(
                List.of(suspend(NOW.minusDays(10), NOW.minusDays(3))), NOW)).isEqualTo(2);
        // 첫 제한(종료)과 두 번째 제한(종료) — 여전히 2 (두 번째는 감형하지 않음)
        assertThat(SanctionService.reductionFor(
                List.of(suspend(NOW.minusDays(30), NOW.minusDays(23)), suspend(NOW.minusDays(10), NOW.minusDays(3))), NOW))
                .isEqualTo(2);
        // 경고만 있으면 감형 없음
        UserSanction warning = UserSanction.of(1L, UserSanction.TYPE_WARNING, null, "w", null);
        assertThat(SanctionService.reductionFor(List.of(warning), NOW)).isZero();
    }

    @Test
    @DisplayName("제재 엔티티: 정지·차단만 로그인을 막고, 해제하면 효력이 없다")
    void sanctionEntity() {
        UserSanction ban = UserSanction.of(2L, UserSanction.TYPE_BAN, null, "영구", 9L);
        assertThat(ban.blocksLogin()).isTrue();
        assertThat(ban.isPermanent()).isTrue();
        assertThat(ban.isEffectiveAt(NOW)).isTrue();
        ban.lift();
        assertThat(ban.isEffectiveAt(NOW)).isFalse();

        UserSanction warning = UserSanction.of(2L, UserSanction.TYPE_WARNING, null, "경고", 9L);
        assertThat(warning.blocksLogin()).isFalse();
        assertThat(warning.hasEnded(NOW)).isFalse();
    }
}
