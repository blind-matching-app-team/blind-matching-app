package com.bma.matching.service;

import com.bma.common.entity.YesNo;
import com.bma.user.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 대기열 결과를 WebSocket(STOMP) 으로 사용자에게 밀어준다.
 *
 * <p>클라이언트는 연결 후 {@code /user/queue/matching} 을 구독한다. 사용자 목적지의 이름은 STOMP CONNECT 때
 * 세운 인증 주체의 이름(이메일)이다. 푸시는 보조 수단이고, 정본은 {@code GET /api/v1/matching/queue} 폴링이다
 * (소켓이 끊겨 있어도 화면이 복구된다).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueuePushNotifier {

    /** 사용자 목적지. 클라이언트 구독 주소는 {@code /user} 접두사를 붙인 {@code /user/queue/matching}. */
    public static final String DESTINATION = "/queue/matching";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    /**
     * 푸시 메시지.
     *
     * @param type          MATCHED / TIMEOUT
     * @param queueId       대기열 항목
     * @param matchId       성사된 매칭(TIMEOUT 이면 null)
     * @param chatRoomId    채팅방(TIMEOUT 이면 null)
     * @param partnerUserId 상대(TIMEOUT 이면 null)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QueuePush(String type, Long queueId, Long matchId, Long chatRoomId, Long partnerUserId) {
    }

    /**
     * 사용자에게 보낸다. 실패해도 호출자의 트랜잭션에 영향을 주지 않는다.
     *
     * @param userId  수신자
     * @param payload 메시지
     */
    public void push(Long userId, QueuePush payload) {
        try {
            userRepository.findByIdAndDeleted(userId, YesNo.N).ifPresent(user ->
                    messagingTemplate.convertAndSendToUser(user.getEmail(), DESTINATION, payload));
        } catch (Exception e) {
            log.warn("대기열 푸시 실패(폴링으로 복구됨): userId={}, type={}", userId, payload.type(), e);
        }
    }
}
