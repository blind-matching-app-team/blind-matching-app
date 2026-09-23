package com.bma.chat.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S11-08: 차단 상대에게 보낸 메시지는 발신자에게만 보인다.
 */
class ChatMessageHiddenTest {

    @Test
    @DisplayName("기본 생성은 숨김이 아니며 누구에게나 보인다")
    void defaultIsVisible() {
        ChatMessage message = ChatMessage.of(1L, 10L, ChatMessage.TYPE_TEXT, "안녕", null);
        assertThat(message.isHidden()).isFalse();
        assertThat(message.getHiddenYn()).isEqualTo("N");
        assertThat(message.isVisibleTo(10L)).isTrue();
        assertThat(message.isVisibleTo(20L)).isTrue();
    }

    @Test
    @DisplayName("숨김 메시지는 발신자 본인에게만 보이고 상대에게는 없는 것으로 친다")
    void hiddenIsVisibleOnlyToSender() {
        ChatMessage message = ChatMessage.of(1L, 10L, ChatMessage.TYPE_TEXT, "잘 지내?", null, true);
        assertThat(message.isHidden()).isTrue();
        assertThat(message.getHiddenYn()).isEqualTo("Y");
        assertThat(message.isVisibleTo(10L)).isTrue();
        assertThat(message.isVisibleTo(20L)).isFalse();
    }
}
