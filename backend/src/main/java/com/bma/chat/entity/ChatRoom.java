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

    /** 목록 응답의 상태값: 대화 가능. */
    public static final String LIST_STATUS_ACTIVE = "ACTIVE";

    /** 목록 응답의 상태값: 매칭 종료로 읽기 전용(S6-12 종료됨 배지). */
    public static final String LIST_STATUS_ENDED = "ENDED";

    /**
     * S6 목록에 내려줄 상태값을 만든다.
     *
     * <p>DB 의 {@code ROOM_STATUS}(ACTIVE/CLOSED)를 화면 계약(ACTIVE/ENDED)으로 바꾼다.
     * 종료된 방은 목록에서 지우지 않고 읽기 전용으로 남기므로(매칭 히스토리 겸용),
     * 프론트는 이 값으로 배지와 입력창 비활성화를 결정한다.</p>
     *
     * @return {@code ACTIVE} 또는 {@code ENDED}
     */
    public String listStatus() {
        return isActive() ? LIST_STATUS_ACTIVE : LIST_STATUS_ENDED;
    }

    /** 방을 종료 처리한다. */
    public void close() {
        this.roomStatus = STATUS_CLOSED;
        this.closeDate = LocalDateTime.now();
    }
}
