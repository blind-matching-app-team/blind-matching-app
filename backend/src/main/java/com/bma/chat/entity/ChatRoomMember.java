package com.bma.chat.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 채팅방 참여자 및 읽음 위치({@code CH_CHAT_ROOM_MEMBER}).
 *
 * <p>이 엔티티는 스키마에는 있었지만 기존 코드에서 전혀 사용되지 않았다.
 * 그 결과 <b>누가 어느 방의 참여자인지 확인할 방법이 없었고</b>,
 * 채팅 조회·전송 API가 방 번호만 바꾸면 통과되는 상태였다.
 * 이제 모든 채팅 접근은 이 테이블을 근거로 검증한다.</p>
 */
@Entity
@Table(name = "CH_CHAT_ROOM_MEMBER")
@IdClass(ChatRoomMember.ChatRoomMemberId.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatRoomMember extends BaseAuditEntity {

    /** 채팅방 ID(PK 일부). */
    @Id
    @Column(name = "CHAT_ROOM_ID")
    private Long chatRoomId;

    /** 참여자 ID(PK 일부). */
    @Id
    @Column(name = "USER_ID")
    private Long userId;

    /** 입장 일시. */
    @Column(name = "JOIN_DATE", nullable = false)
    private LocalDateTime joinDate = LocalDateTime.now();

    /** 퇴장 일시. 값이 있으면 더 이상 참여자가 아니다. */
    @Column(name = "LEAVE_DATE")
    private LocalDateTime leaveDate;

    /** 마지막으로 읽은 메시지 ID. 안 읽은 개수 계산에 사용한다. */
    @Column(name = "LAST_READ_MESSAGE_ID")
    private Long lastReadMessageId;

    /** 채팅 알림 수신 여부. */
    @Column(name = "NOTIFICATION_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String notificationYn = YesNo.Y;

    /**
     * 참여자 정보를 만든다.
     *
     * @param chatRoomId 채팅방 ID
     * @param userId     사용자 ID
     * @return 저장 대상 엔티티
     */
    public static ChatRoomMember join(Long chatRoomId, Long userId) {
        ChatRoomMember member = new ChatRoomMember();
        member.chatRoomId = chatRoomId;
        member.userId = userId;
        member.joinDate = LocalDateTime.now();
        return member;
    }

    /**
     * 현재 참여 중인지 확인한다.
     *
     * @return 퇴장하지 않았고 논리 삭제되지 않았으면 {@code true}
     */
    public boolean isActiveMember() {
        return leaveDate == null && !isDeleted();
    }

    /**
     * 읽음 위치를 갱신한다.
     *
     * @param messageId 마지막으로 읽은 메시지 ID
     */
    public void updateLastRead(Long messageId) {
        if (messageId == null) {
            return;
        }
        // 읽음 위치가 뒤로 가지 않도록 최댓값을 유지한다.
        if (this.lastReadMessageId == null || messageId > this.lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }

    /**
     * 복합 기본키 클래스.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class ChatRoomMemberId implements Serializable {

        /** 채팅방 ID. */
        private Long chatRoomId;

        /** 사용자 ID. */
        private Long userId;
    }
}
