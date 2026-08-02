package com.bma.chat.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 매칭별 1:1 채팅방({@code CH_CHAT_ROOM}).
 *
 * <p>{@code UK_CH_CHAT_ROOM_MATCH} 제약에 따라 매칭 1건당 방은 하나뿐이다.</p>
 */
@Entity
@Table(name = "CH_CHAT_ROOM")
@Getter
@Setter
@NoArgsConstructor
public class ChatRoom extends BaseAuditEntity {

    /** 방 상태: 사용 중. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 방 상태: 종료됨. */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 채팅방 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CHAT_ROOM_ID")
    private Long id;

    /** 이 방이 속한 매칭 ID. */
    @Column(name = "MATCH_ID", nullable = false)
    private Long matchId;

    /** 방 상태. */
    @Column(name = "ROOM_STATUS", nullable = false, length = 20)
    private String roomStatus = STATUS_ACTIVE;

    /** 마지막 메시지 ID. 목록 화면에서 미리보기를 만들 때 사용한다. */
    @Column(name = "LAST_MESSAGE_ID")
    private Long lastMessageId;

    /** 마지막 메시지 일시. */
    @Column(name = "LAST_MESSAGE_DATE")
    private LocalDateTime lastMessageDate;

    /** 방 종료 일시. */
    @Column(name = "CLOSE_DATE")
    private LocalDateTime closeDate;

    /**
     * 매칭 성사 시 채팅방을 만든다.
     *
     * @param matchId 매칭 ID
     * @return 저장 대상 엔티티
     */
    public static ChatRoom openFor(Long matchId) {
        ChatRoom room = new ChatRoom();
        room.matchId = matchId;
        room.roomStatus = STATUS_ACTIVE;
        return room;
    }

    /**
     * 메시지 발생을 반영한다.
     *
     * @param messageId 메시지 ID
     * @param sentAt    전송 일시
     */
    public void touchLastMessage(Long messageId, LocalDateTime sentAt) {
        this.lastMessageId = messageId;
        this.lastMessageDate = sentAt;
    }

    /**
     * 사용 중인 방인지 확인한다.
     *
     * @return 사용 중이면 {@code true}
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(roomStatus) && !isDeleted();
    }

    /** 방을 종료 처리한다. */
    public void close() {
        this.roomStatus = STATUS_CLOSED;
        this.closeDate = LocalDateTime.now();
    }
}
