package com.bma.chat.repository;

import com.bma.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * {@link ChatMessage} 저장소.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 방의 메시지를 최신순으로 페이지 조회한다(숨김 포함, 관리·검토용).
     *
     * @param chatRoomId 채팅방 ID
     * @param deleted    논리 삭제 여부
     * @param pageable   페이지 정보
     * @return 메시지 페이지
     */
    Page<ChatMessage> findByChatRoomIdAndDeletedOrderByIdDesc(Long chatRoomId, String deleted, Pageable pageable);

    /**
     * 특정 사용자에게 보여도 되는 메시지를 최신순으로 페이지 조회한다 (S11-06 이력, GET /chat/rooms/{id}/messages).
     *
     * <p>차단 상대에게 보낸 숨김 메시지는 발신자 본인에게만 보인다(S11-08).</p>
     *
     * @param chatRoomId   채팅방 ID
     * @param viewerUserId 보는 사람
     * @param pageable     페이지 정보
     * @return 메시지 페이지
     */
    @Query(value = """
            select m from ChatMessage m
            where m.chatRoomId = :chatRoomId
              and m.deleted = 'N'
              and (m.hiddenYn = 'N' or m.senderUserId = :viewerUserId)
            order by m.id desc
            """,
            countQuery = """
            select count(m) from ChatMessage m
            where m.chatRoomId = :chatRoomId
              and m.deleted = 'N'
              and (m.hiddenYn = 'N' or m.senderUserId = :viewerUserId)
            """)
    Page<ChatMessage> findVisibleTo(@Param("chatRoomId") Long chatRoomId,
                                    @Param("viewerUserId") Long viewerUserId,
                                    Pageable pageable);

    /**
     * 방의 첫 메시지 전송 시각을 조회한다. 누적 대화 시간 계산에 사용한다.
     *
     * @param chatRoomId 채팅방 ID
     * @return 첫 메시지 시각
     */
    @Query("select min(m.sendDate) from ChatMessage m where m.chatRoomId = :chatRoomId and m.deleted = 'N'")
    Optional<LocalDateTime> findFirstSendDate(@Param("chatRoomId") Long chatRoomId);

    /**
     * 방에서 특정 사용자가 보낸 메시지 수(Reveal 각자 메시지 조건). 숨김 여부와 무관하게 본인 것은 다 센다.
     */
    long countByChatRoomIdAndSenderUserIdAndDeleted(Long chatRoomId, Long senderUserId, String deleted);

    /**
     * 사용자가 아직 읽지 않은 메시지 수를 센다. 숨김 메시지는 받는 쪽에 존재하지 않는 것으로 친다.
     *
     * @param chatRoomId        채팅방 ID
     * @param lastReadMessageId 마지막으로 읽은 메시지 ID(없으면 0)
     * @param userId            사용자 ID(본인이 보낸 메시지는 제외)
     * @return 안 읽은 메시지 수
     */
    @Query("""
            select count(m) from ChatMessage m
            where m.chatRoomId = :chatRoomId
              and m.deleted = 'N'
              and m.hiddenYn = 'N'
              and m.senderUserId <> :userId
              and m.id > :lastReadMessageId
            """)
    long countUnread(@Param("chatRoomId") Long chatRoomId,
                     @Param("lastReadMessageId") Long lastReadMessageId,
                     @Param("userId") Long userId);
}
