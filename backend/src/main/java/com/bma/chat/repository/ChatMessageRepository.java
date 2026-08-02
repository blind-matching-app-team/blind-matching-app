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
     * 방의 메시지를 최신순으로 페이지 조회한다.
     *
     * @param chatRoomId 채팅방 ID
     * @param deleted    논리 삭제 여부
     * @param pageable   페이지 정보
     * @return 메시지 페이지
     */
    Page<ChatMessage> findByChatRoomIdAndDeletedOrderByIdDesc(Long chatRoomId, String deleted, Pageable pageable);

    /**
     * 방의 첫 메시지 전송 시각을 조회한다. 누적 대화 시간 계산에 사용한다.
     *
     * @param chatRoomId 채팅방 ID
     * @return 첫 메시지 시각
     */
    @Query("select min(m.sendDate) from ChatMessage m where m.chatRoomId = :chatRoomId and m.deleted = 'N'")
    Optional<LocalDateTime> findFirstSendDate(@Param("chatRoomId") Long chatRoomId);

    /**
     * 사용자가 아직 읽지 않은 메시지 수를 센다.
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
              and m.senderUserId <> :userId
              and m.id > :lastReadMessageId
            """)
    long countUnread(@Param("chatRoomId") Long chatRoomId,
                     @Param("lastReadMessageId") Long lastReadMessageId,
                     @Param("userId") Long userId);
}
