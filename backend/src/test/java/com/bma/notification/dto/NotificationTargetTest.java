package com.bma.notification.dto;

import com.bma.notification.dto.NotificationDtos.NotificationResponse;
import com.bma.notification.dto.NotificationDtos.NotificationTarget;
import com.bma.notification.entity.Notification;
import com.bma.notification.entity.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7-09 "클릭 시 유형별 화면 이동" 계약을 고정한다.
 *
 * <p>프론트는 {@code eventCode} 로 아이콘·문구를, {@code target.screen} 과 ID 로 라우트를 정한다.
 * 사건과 화면의 대응이 바뀌면 화면이 엉뚱한 곳으로 가므로 여기서 잡는다.</p>
 */
class NotificationTargetTest {

    @Test
    @DisplayName("사건별 이동 화면: 매칭 성사·요청·단계상승은 S10, 메시지는 S11, 종료·호감은 S5")
    void targetScreenByEvent() {
        assertThat(NotificationEvent.MATCH_CREATED.targetScreen()).isEqualTo("S10");
        assertThat(NotificationEvent.REVEAL_REQUESTED.targetScreen()).isEqualTo("S10");
        assertThat(NotificationEvent.REVEAL_LEVEL_UP.targetScreen()).isEqualTo("S10");
        assertThat(NotificationEvent.MATCH_UNVERIFIED_PARTNER.targetScreen()).isEqualTo("S10");
        assertThat(NotificationEvent.MESSAGE_RECEIVED.targetScreen()).isEqualTo("S11");
        assertThat(NotificationEvent.MATCH_ENDED.targetScreen()).isEqualTo("S5");
        assertThat(NotificationEvent.MATCH_LIKED.targetScreen()).isEqualTo("S5");
        // 경고 조치는 상세 화면이 아직 없다(S7-13 "추후 설계").
        assertThat(NotificationEvent.WARNING_ISSUED.targetScreen()).isNull();
    }

    @Test
    @DisplayName("참조 유형에 따라 matchId / chatRoomId / userId 중 맞는 자리에만 ID 가 들어간다")
    void targetIdsFollowReferenceType() {
        NotificationTarget match = NotificationTarget.of(NotificationEvent.MATCH_CREATED, "MATCH", 12L);
        assertThat(match).isEqualTo(new NotificationTarget("S10", 12L, null, null));

        NotificationTarget room = NotificationTarget.of(NotificationEvent.MESSAGE_RECEIVED, "CHAT_ROOM", 7L);
        assertThat(room).isEqualTo(new NotificationTarget("S11", null, 7L, null));

        NotificationTarget liked = NotificationTarget.of(NotificationEvent.MATCH_LIKED, "USER", 34L);
        assertThat(liked).isEqualTo(new NotificationTarget("S5", null, null, 34L));
    }

    @Test
    @DisplayName("사건 코드가 없거나 모르는 값(V9 이전 행)은 SYSTEM 으로 취급하고 이동 없음")
    void unknownEventFallsBackToSystem() {
        Notification legacy = Notification.of(1L, NotificationEvent.MATCH_CREATED, "t", "c", "MATCH", 1L);
        legacy.setEventCode("SOMETHING_OLD");

        NotificationResponse response = NotificationResponse.from(legacy);
        assertThat(response.eventCode()).isEqualTo("SYSTEM");
        assertThat(response.target().screen()).isNull();
        // 분류(type)는 저장된 값 그대로다.
        assertThat(response.type()).isEqualTo(Notification.TYPE_MATCH);
    }

    @Test
    @DisplayName("분류(type)는 사건에서 파생된다 — 생성처가 따로 지정하지 않는다")
    void categoryDerivedFromEvent() {
        Notification n = Notification.of(1L, NotificationEvent.REVEAL_REQUESTED, "t", null, "MATCH", 9L);
        assertThat(n.getNotificationType()).isEqualTo(Notification.TYPE_REVEAL);
        assertThat(n.getEventCode()).isEqualTo("REVEAL_REQUESTED");
        assertThat(NotificationResponse.from(n).target().matchId()).isEqualTo(9L);
    }
}
