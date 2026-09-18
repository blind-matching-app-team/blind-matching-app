package com.bma.chat.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S6 목록의 상태값과 나가기/재입장 규칙을 고정한다.
 *
 * <p>매칭이 끝난 방은 목록에서 지우지 않고 {@code ENDED} 로 남긴다(매칭 히스토리 겸용, BMA-49 결정).
 * 나가기(S6-11)는 내 목록에서만 빠지는 것이라 방 자체는 건드리지 않는다.</p>
 */
class ChatRoomListStatusTest {

    @Test
    @DisplayName("사용 중인 방은 ACTIVE, 종료된 방은 ENDED 로 내려준다")
    void listStatusFollowsRoomStatus() {
        ChatRoom room = ChatRoom.openFor(1L);
        assertThat(room.listStatus()).isEqualTo(ChatRoom.LIST_STATUS_ACTIVE);

        room.close();
        assertThat(room.listStatus()).isEqualTo(ChatRoom.LIST_STATUS_ENDED);
        assertThat(room.isActive()).isFalse();
    }

    @Test
    @DisplayName("나가면 목록에서 빠지고, 다시 들어오면 참여 상태로 돌아온다")
    void leaveAndRejoin() {
        ChatRoomMember member = ChatRoomMember.join(1L, 10L);
        assertThat(member.isActiveMember()).isTrue();
        assertThat(member.hasLeft()).isFalse();

        member.leave();
        assertThat(member.isActiveMember()).isFalse();
        assertThat(member.hasLeft()).isTrue();

        member.rejoin();
        assertThat(member.isActiveMember()).isTrue();
        assertThat(member.hasLeft()).isFalse();
    }

    @Test
    @DisplayName("읽음 위치는 뒤로 가지 않는다 — 나갈 때 마지막 메시지로 맞춰 두면 재입장 후 새 메시지만 센다")
    void lastReadNeverMovesBackward() {
        ChatRoomMember member = ChatRoomMember.join(1L, 10L);
        member.updateLastRead(50L);
        member.updateLastRead(30L);
        assertThat(member.getLastReadMessageId()).isEqualTo(50L);
        member.updateLastRead(null);
        assertThat(member.getLastReadMessageId()).isEqualTo(50L);
    }
}
