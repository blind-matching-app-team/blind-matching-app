package com.bma.chat.service;

import com.bma.chat.dto.ChatDtos.ChatRoomResponse;
import com.bma.chat.dto.ChatDtos.MessageResponse;
import com.bma.chat.dto.ChatDtos.ReadResult;
import com.bma.chat.dto.ChatDtos.SendMessageRequest;
import com.bma.chat.entity.ChatMessage;
import com.bma.chat.entity.ChatRoom;
import com.bma.chat.entity.ChatRoomMember;
import com.bma.chat.repository.ChatMessageRepository;
import com.bma.chat.repository.ChatRoomMemberRepository;
import com.bma.chat.repository.ChatRoomRepository;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.response.PageResponse;
import com.bma.common.security.ChatRoomAccessChecker;
import com.bma.notification.entity.Notification;
import com.bma.notification.service.NotificationService;
import com.bma.reveal.entity.RevealProgress;
import com.bma.reveal.repository.RevealProgressRepository;
import com.bma.reveal.service.RevealService;
import com.bma.safety.service.SafetyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 채팅방 및 메시지 처리.
 *
 * <p>고친 점 — 기존 채팅 구현에는 이 프로젝트에서 가장 심각한 결함들이 있었다.</p>
 * <ul>
 *   <li>{@code GET /chat/rooms}가 {@code findAll()}로 <b>전체 사용자의 모든 채팅방</b>을 반환했다.</li>
 *   <li>{@code GET /chat/rooms/{id}/messages}에 참여자 검증이 없어 방 번호만 바꾸면
 *       <b>남의 대화 이력을 그대로 읽을 수 있었다</b>.</li>
 *   <li>메시지 저장 시 발신자를 클라이언트 페이로드에서 받아 <b>사칭이 가능</b>했다.</li>
 * </ul>
 *
 * <p>이제 모든 진입점이 {@link #requireMember(Long, Long)}를 거치며, 발신자는 항상
 * 인증 주체에서 결정된다. WebSocket 인터셉터도 {@link ChatRoomAccessChecker}를 통해
 * 같은 검증 로직을 사용한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService implements ChatRoomAccessChecker {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final RevealProgressRepository revealProgressRepository;
    private final RevealService revealService;
    private final SafetyService safetyService;
    private final NotificationService notificationService;

    /**
     * {@inheritDoc}
     *
     * <p>WebSocket 인터셉터가 SUBSCRIBE/SEND 프레임마다 호출한다.
     * 예외를 던지지 않고 boolean만 반환해 인터셉터가 응답 형식을 결정하게 한다.</p>
     */
    @Override
    public boolean canAccessRoom(Long userId, Long roomId) {
        return memberRepository.isActiveMember(roomId, userId);
    }

    /**
     * 내가 참여 중인 채팅방 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 최근 대화순 채팅방 목록
     */
    public List<ChatRoomResponse> getMyRooms(Long userId) {
        List<ChatRoom> rooms = roomRepository.findRoomsOfUser(userId);
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        List<Long> matchIds = rooms.stream().map(ChatRoom::getMatchId).toList();

        // 방마다 개별 조회하면 N+1이 되므로 참여자와 공개 단계를 한 번에 가져온다.
        List<ChatRoomMember> members = memberRepository.findByChatRoomIdInAndDeleted(roomIds, YesNo.N);
        Map<Long, List<ChatRoomMember>> membersByRoom = members.stream()
                .collect(Collectors.groupingBy(ChatRoomMember::getChatRoomId));
        Map<Long, RevealProgress> progressByMatch = revealProgressRepository
                .findByIdInAndDeleted(matchIds, YesNo.N).stream()
                .collect(Collectors.toMap(RevealProgress::getId, Function.identity()));

        return rooms.stream()
                .map(room -> toRoomResponse(room, userId,
                        membersByRoom.getOrDefault(room.getId(), List.of()),
                        progressByMatch.get(room.getMatchId())))
                .toList();
    }

    /**
     * 채팅방의 메시지 이력을 조회한다.
     *
     * @param userId 요청자
     * @param roomId 채팅방 ID
     * @param page   페이지 번호(0부터, 최신순)
     * @param size   페이지 크기(최대 100)
     * @return 메시지 페이지
     * @throws BusinessException 참여자가 아닌 경우
     */
    public PageResponse<MessageResponse> getMessages(Long userId, Long roomId, int page, int size) {
        requireMember(userId, roomId);

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.of(
                messageRepository.findByChatRoomIdAndDeletedOrderByIdDesc(roomId, YesNo.N, pageable),
                MessageResponse::from);
    }

    /**
     * 메시지를 전송한다.
     *
     * @param senderUserId 발신자(반드시 인증 주체에서 전달받은 값)
     * @param roomId       채팅방 ID
     * @param request      전송 요청
     * @return 저장된 메시지
     * @throws BusinessException 참여자가 아니거나, 방이 종료되었거나, 차단 관계인 경우
     */
    @Transactional
    public MessageResponse sendMessage(Long senderUserId, Long roomId, SendMessageRequest request) {
        ChatRoom room = requireActiveRoom(roomId);
        requireMember(senderUserId, roomId);

        Long partnerId = findPartnerId(roomId, senderUserId);
        // 차단한(또는 차단당한) 상대에게는 메시지를 보낼 수 없다.
        if (partnerId != null && safetyService.isBlockedBetween(senderUserId, partnerId)) {
            throw new BusinessException(ErrorCode.BLOCKED_RELATION);
        }

        String messageType = (request.messageType() == null || request.messageType().isBlank())
                ? ChatMessage.TYPE_TEXT
                : request.messageType();
        // SYSTEM/REVEAL 유형은 서버만 생성할 수 있다.
        if (!ChatMessage.CLIENT_ALLOWED_TYPES.contains(messageType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "허용되지 않는 메시지 유형입니다.");
        }

        ChatMessage message = messageRepository.save(ChatMessage.of(
                roomId, senderUserId, messageType, request.content(), request.replyMessageId()));

        room.touchLastMessage(message.getId(), message.getSendDate());

        // 보낸 사람은 자기 메시지를 읽은 것으로 처리한다.
        memberRepository.findByChatRoomIdAndUserId(roomId, senderUserId)
                .ifPresent(member -> member.updateLastRead(message.getId()));

        // 대화량이 쌓여야 다음 공개 단계로 올라갈 수 있으므로 진행 상태에 반영한다.
        revealService.recordMessage(room.getMatchId(), calculateChatMinutes(roomId));

        if (partnerId != null) {
            notificationService.notify(partnerId, Notification.TYPE_MESSAGE,
                    "새 메시지가 도착했어요", preview(request.content()), "CHAT_ROOM", roomId);
        }

        return MessageResponse.from(message);
    }

    /**
     * 읽음 위치를 갱신한다.
     *
     * @param userId    요청자
     * @param roomId    채팅방 ID
     * @param messageId 마지막으로 읽은 메시지 ID
     * @return 갱신 결과
     * @throws BusinessException 참여자가 아닌 경우
     */
    @Transactional
    public ReadResult markRead(Long userId, Long roomId, Long messageId) {
        ChatRoomMember member = requireMember(userId, roomId);
        member.updateLastRead(messageId);
        return new ReadResult(roomId, member.getLastReadMessageId());
    }

    /**
     * 매칭 성사 시 채팅방과 참여자를 생성한다.
     *
     * <p>{@code MatchingService}가 호출한다. 이미 방이 있으면 그대로 반환한다
     * ({@code UK_CH_CHAT_ROOM_MATCH} 제약 위반 방지).</p>
     *
     * @param matchId 매칭 ID
     * @param userIdA 참여자 A
     * @param userIdB 참여자 B
     * @return 생성되었거나 이미 존재하던 채팅방
     */
    @Transactional
    public ChatRoom createRoomForMatch(Long matchId, Long userIdA, Long userIdB) {
        return roomRepository.findByMatchIdAndDeleted(matchId, YesNo.N)
                .orElseGet(() -> {
                    ChatRoom room = roomRepository.save(ChatRoom.openFor(matchId));
                    memberRepository.saveAll(List.of(
                            ChatRoomMember.join(room.getId(), userIdA),
                            ChatRoomMember.join(room.getId(), userIdB)));
                    log.info("채팅방 생성: matchId={}, roomId={}", matchId, room.getId());
                    return room;
                });
    }

    /**
     * 요청자가 방의 활성 참여자인지 확인한다.
     *
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 참여자 정보
     * @throws BusinessException 참여자가 아닌 경우
     */
    private ChatRoomMember requireMember(Long userId, Long roomId) {
        return memberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .filter(ChatRoomMember::isActiveMember)
                // 존재하지 않는 방과 권한 없는 방을 같은 오류로 응답해
                // 방 ID를 훑어 존재 여부를 알아내는 것을 막는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_MEMBER));
    }

    /**
     * 사용 중인 채팅방을 조회한다.
     *
     * @param roomId 채팅방 ID
     * @return 채팅방
     * @throws BusinessException 방이 없거나 종료된 경우
     */
    private ChatRoom requireActiveRoom(Long roomId) {
        ChatRoom room = roomRepository.findByIdAndDeleted(roomId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_MEMBER));
        if (!room.isActive()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        return room;
    }

    /**
     * 방의 상대 참여자 ID를 찾는다.
     *
     * @param roomId 채팅방 ID
     * @param userId 나의 ID
     * @return 상대 ID. 1:1 방이 아니면 {@code null}
     */
    private Long findPartnerId(Long roomId, Long userId) {
        return memberRepository.findByChatRoomIdAndDeleted(roomId, YesNo.N).stream()
                .filter(ChatRoomMember::isActiveMember)
                .map(ChatRoomMember::getUserId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 첫 메시지 이후 경과 시간(분)을 계산한다.
     *
     * @param roomId 채팅방 ID
     * @return 경과 분. 첫 메시지가 없으면 0
     */
    private int calculateChatMinutes(Long roomId) {
        return messageRepository.findFirstSendDate(roomId)
                .map(first -> (int) Math.min(Duration.between(first, LocalDateTime.now()).toMinutes(),
                        Integer.MAX_VALUE))
                .orElse(0);
    }

    /**
     * 방 하나를 응답 DTO로 변환한다.
     *
     * @param room     채팅방
     * @param userId   요청자
     * @param members  방 참여자 목록
     * @param progress 공개 단계 진행 상태(없을 수 있음)
     * @return 응답 DTO
     */
    private ChatRoomResponse toRoomResponse(ChatRoom room, Long userId,
                                            List<ChatRoomMember> members, RevealProgress progress) {
        Long partnerId = members.stream()
                .filter(ChatRoomMember::isActiveMember)
                .map(ChatRoomMember::getUserId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElse(null);

        Long lastReadMessageId = members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .map(ChatRoomMember::getLastReadMessageId)
                .findFirst()
                .orElse(null);

        long unreadCount = messageRepository.countUnread(
                room.getId(), lastReadMessageId == null ? 0L : lastReadMessageId, userId);

        return new ChatRoomResponse(
                room.getId(),
                room.getMatchId(),
                partnerId,
                room.getRoomStatus(),
                room.getLastMessageDate(),
                unreadCount,
                progress == null ? 0 : progress.getCurrentLevel());
    }

    /**
     * 알림에 넣을 메시지 미리보기를 만든다.
     *
     * @param content 원문
     * @return 최대 50자로 자른 미리보기
     */
    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 50 ? content : content.substring(0, 50) + "...";
    }
}
