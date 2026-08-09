package com.bma.chat.controller;

import com.bma.chat.dto.ChatDtos.MessageResponse;
import com.bma.chat.dto.ChatDtos.SendMessageRequest;
import com.bma.chat.service.ChatService;
import com.bma.common.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 채팅 메시지 핸들러.
 *
 * <p>고친 점 — 기존 구현은 다음과 같이 동작했다.</p>
 * <pre>
 * public record SendMessage(Long senderId, String messageType, String content) {}
 * m.setSenderUserId(r.senderId());   // 클라이언트가 준 값을 그대로 저장
 * </pre>
 * <p>여기에 STOMP 인증도 없었기 때문에, 인증 없이 접속해 아무 사용자나 사칭하고
 * 아무 방에나 메시지를 보낼 수 있었다.</p>
 *
 * <p>지금은</p>
 * <ul>
 *   <li>{@code StompAuthChannelInterceptor}가 CONNECT에서 JWT를 검증하고,
 *       SEND 프레임마다 방 참여자인지 확인한다.</li>
 *   <li>발신자는 {@link Principal}(= 인증된 세션)에서만 가져온다.
 *       페이로드에는 발신자 필드 자체가 없다.</li>
 * </ul>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 메시지 전송 처리.
     *
     * <p>목적지: {@code /app/chat/{roomId}/send}</p>
     * <p>브로드캐스트: {@code /topic/chat/{roomId}}</p>
     *
     * @param roomId    채팅방 ID
     * @param request   전송 요청(발신자 정보 없음)
     * @param principal 인증된 세션 주체
     */
    @MessageMapping("/chat/{roomId}/send")
    public void send(@DestinationVariable Long roomId,
                     @Valid @Payload SendMessageRequest request,
                     Principal principal) {

        // 인터셉터에서 이미 검증했지만, 핸들러가 직접 호출되는 경로가 생길 수 있으므로 한 번 더 확인한다.
        CustomUserPrincipal sender = CustomUserPrincipal.from(principal);

        MessageResponse saved = chatService.sendMessage(sender.userId(), roomId, request);

        // 구독 권한 역시 인터셉터가 검사하므로, 이 토픽은 방 참여자에게만 전달된다.
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, saved);

        log.debug("채팅 메시지 전송: roomId={}, senderId={}, messageId={}",
                roomId, sender.userId(), saved.messageId());
    }
}
