package com.bma.chat.repository;

import com.bma.chat.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link ChatRoomMember} 저장소.
 *
 * <p>채팅 권한 검증의 근거가 되는 저장소다.</p>
 */
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMember.ChatRoomMemberId> {

    /**
     * 특정 방의 참여자 정보를 조회한다.
     *
     * @param chatRoomId 채팅방 ID
     * @param userId     사용자 ID
     * @return 참여자 정보
     */
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    /**
     * 방의 모든 참여자를 조회한다.
     *
     * @param chatRoomId 채팅방 ID
     * @param deleted    논리 삭제 여부
     * @return 참여자 목록
     */
    List<ChatRoomMember> findByChatRoomIdAndDeleted(Long chatRoomId, String deleted);

    /**
     * 여러 방의 참여자를 한 번에 조회한다. 목록 화면의 N+1을 막는다.
     *
     * @param chatRoomIds 채팅방 ID 목록
     * @param deleted     논리 삭제 여부
     * @return 참여자 목록
     */
    List<ChatRoomMember> findByChatRoomIdInAndDeleted(List<Long> chatRoomIds, String deleted);

    /**
     * 사용자가 해당 방의 활성 참여자인지 확인한다.
     *
     * <p>WebSocket 인터셉터와 REST 컨트롤러가 공통으로 사용하는 권한 검사 쿼리다.</p>
     *
     * @param chatRoomId 채팅방 ID
     * @param userId     사용자 ID
     * @return 참여자이면 {@code true}
     */
    @Query("""
            select count(m) > 0 from ChatRoomMember m
            where m.chatRoomId = :chatRoomId
              and m.userId = :userId
              and m.leaveDate is null
              and m.deleted = 'N'
            """)
    boolean isActiveMember(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);
}
