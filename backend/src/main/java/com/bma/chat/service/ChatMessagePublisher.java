package com.bma.chat.service;

import com.bma.chat.dto.ChatDtos.MessageResponse;
import com.bma.chat.dto.ChatDtos.SendResult;
import com.bma.common.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 저장된 채팅 메시지를 구독자에게 실시간으로 밀어 준다 (BMA-73).
 *
 * <p>REST 전송과 STOMP 전송이 같은 규칙으로 배달되도록 한 곳에 모았다.</p>
 * <ul>
 *   <li>일반 메시지: {@code /topic/chat/{roomId}} 브로드캐스트. 방 참여자만 구독할 수 있다(인터셉터).</li>
 *   <li>차단 상대에게 보낸(숨김) 메시지: 상대에게는 아무것도 가지 않고, 발신자의 개인 큐
 *       {@code /user/queue/chat} 로만 되돌려 준다. 발신자 화면은 정상 전송처럼 보인다(S11-08).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {

    /** 방 브로드캐스트 목적지 접두사. */
    public static final String ROOM_TOPIC_PREFIX = "/topic/chat/";
    /** 발신자 개인 에코 목적지(사용자 접두사 {@code /user} 뒤). */
    public static final String USER_ECHO_QUEUE = "/queue/chat";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 전송 결과를 배달한다.
     *
     * @param sender 발신자(인증 주체). 개인 큐는 세션 사용자명(이메일)으로 라우팅된다
     * @param result 저장 결과
     */
    public void publish(CustomUserPrincipal sender, SendResult result) {
        MessageResponse message = result.message();
        if (result.hidden()) {
            messagingTemplate.convertAndSendToUser(sender.getUsername(), USER_ECHO_QUEUE, message);
            log.debug("숨김 메시지 발신자 에코: roomId={}, senderId={}, messageId={}",
                    message.chatRoomId(), sender.userId(), message.messageId());
            return;
        }
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + message.chatRoomId(), message);
    }
}
