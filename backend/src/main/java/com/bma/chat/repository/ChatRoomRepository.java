package com.bma.chat.repository;

import com.bma.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link ChatRoom} 저장소.
 */
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 매칭에 연결된 채팅방을 조회한다.
     *
     * @param matchId 매칭 ID
     * @param deleted 논리 삭제 여부
     * @return 채팅방
     */
    Optional<ChatRoom> findByMatchIdAndDeleted(Long matchId, String deleted);

    /**
     * 살아 있는 채팅방을 조회한다.
     *
     * @param id      채팅방 ID
     * @param deleted 논리 삭제 여부
     * @return 채팅방
     */
    Optional<ChatRoom> findByIdAndDeleted(Long id, String deleted);

    /**
     * 사용자가 참여 중인 채팅방을 최근 대화순으로 조회한다.
     *
     * <p>기존 구현은 {@code findAll()}로 <b>전체 사용자의 모든 채팅방</b>을 반환하고 있었다.
     * 참여자 테이블과 조인해 본인 방만 나오도록 고쳤다.</p>
     *
     * @param userId 사용자 ID
     * @return 채팅방 목록
     */
    @Query("""
            select r from ChatRoom r
            where r.deleted = 'N'
              and exists (
                    select 1 from ChatRoomMember m
                    where m.chatRoomId = r.id
                      and m.userId = :userId
                      and m.leaveDate is null
                      and m.deleted = 'N')
            order by case when r.lastMessageDate is null then r.insertDate else r.lastMessageDate end desc
            """)
    List<ChatRoom> findRoomsOfUser(@Param("userId") Long userId);
}
