package com.bma.safety.entity;

import com.bma.common.entity.YesNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserSanction} 판정 규칙 테스트.
 *
 * <p>로그인 차단 여부와 영구/기간제 구분이 이용정지 화면의 분기 근거이므로 회귀로 고정한다.</p>
 */
class UserSanctionTest {

    @Test
    @DisplayName("경고와 기능 제한은 로그인을 막지 않는다")
    void warningAndLimits_doNotBlockLogin() {
        assertThat(sanction(UserSanction.TYPE_WARNING, null).blocksLogin()).isFalse();
        assertThat(sanction(UserSanction.TYPE_CHAT_LIMIT, null).blocksLogin()).isFalse();
        assertThat(sanction(UserSanction.TYPE_MATCH_LIMIT, null).blocksLogin()).isFalse();
    }

    @Test
    @DisplayName("정지와 영구정지는 로그인을 막는다")
    void suspendAndBan_blockLogin() {
        assertThat(sanction(UserSanction.TYPE_SUSPEND, LocalDateTime.now().plusDays(3)).blocksLogin()).isTrue();
        assertThat(sanction(UserSanction.TYPE_BAN, null).blocksLogin()).isTrue();
    }

    @Test
    @DisplayName("BAN 은 종료 일시가 있어도 영구로 본다")
    void ban_isAlwaysPermanent() {
        assertThat(sanction(UserSanction.TYPE_BAN, LocalDateTime.now().plusDays(3)).isPermanent()).isTrue();
    }

    @Test
    @DisplayName("종료 일시가 없는 정지는 영구로 본다")
    void suspendWithoutEndDate_isPermanent() {
        // 기간제로 취급하면 해제 시각이 null 인 채로 "언젠가 풀린다"고 잘못 안내하게 된다.
        assertThat(sanction(UserSanction.TYPE_SUSPEND, null).isPermanent()).isTrue();
    }

    @Test
    @DisplayName("종료 일시가 지난 제재는 효력이 없다")
    void expiredSanction_isNotEffective() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(sanction(UserSanction.TYPE_SUSPEND, now.minusDays(1)).isEffectiveAt(now)).isFalse();
        assertThat(sanction(UserSanction.TYPE_SUSPEND, now.plusDays(1)).isEffectiveAt(now)).isTrue();
    }

    @Test
    @DisplayName("해제된 제재는 효력이 없다")
    void inactiveSanction_isNotEffective() {
        UserSanction sanction = sanction(UserSanction.TYPE_SUSPEND, LocalDateTime.now().plusDays(1));
        sanction.setActiveYn(YesNo.N);

        assertThat(sanction.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    private UserSanction sanction(String type, LocalDateTime endDate) {
        UserSanction sanction = new UserSanction();
        sanction.setUserId(1L);
        sanction.setSanctionType(type);
        sanction.setStartDate(LocalDateTime.now().minusDays(1));
        sanction.setEndDate(endDate);
        sanction.setReason("테스트 사유");
        return sanction;
    }
}
