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
import java.util.Set;

/**
 * WebSocket 실시간 채팅 메시지({@code CH_CHAT_MESSAGE}).
 */
@Entity
@Table(name = "CH_CHAT_MESSAGE")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage extends BaseAuditEntity {

    /** 일반 텍스트 메시지. */
    public static final String TYPE_TEXT = "TEXT";

    /** 이미지 메시지. */
    public static final String TYPE_IMAGE = "IMAGE";

    /** 시스템 안내 메시지. */
    public static final String TYPE_SYSTEM = "SYSTEM";

    /** 공개 단계 변경 안내 메시지. */
    public static final String TYPE_REVEAL = "REVEAL";

    /** 클라이언트가 보낼 수 있는 메시지 유형. SYSTEM/REVEAL은 서버만 생성한다. */
    public static final Set<String> CLIENT_ALLOWED_TYPES = Set.of(TYPE_TEXT, TYPE_IMAGE);

    /** 메시지 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MESSAGE_ID")
    private Long id;

    /** 채팅방 ID. */
    @Column(name = "CHAT_ROOM_ID", nullable = false)
    private Long chatRoomId;

    /**
     * 발신자 ID.
     *
     * <p><b>중요</b>: 이 값은 반드시 인증된 세션의 주체에서 가져와야 한다.
     * 기존 구현은 클라이언트가 보낸 페이로드의 {@code senderId}를 그대로 저장해
     * 누구든 타인을 사칭할 수 있었다.</p>
     */
    @Column(name = "SENDER_USER_ID", nullable = false)
    private Long senderUserId;

    /** 메시지 유형. */
    @Column(name = "MESSAGE_TYPE", nullable = false, length = 20)
    private String messageType = TYPE_TEXT;

    /**
     * 메시지 내용.
     *
     * <p>스키마 컬럼이 {@code TEXT}인데 Hibernate는 String을 기본적으로 {@code VARCHAR}로
     * 기대하므로, {@code ddl-auto=validate}를 통과하도록 타입을 명시한다.</p>
     */
    @Column(name = "MESSAGE_CONTENT", columnDefinition = "TEXT")
    private String messageContent;

    /** 첨부 파일 URL. */
    @Column(name = "FILE_URL", length = 1000)
    private String fileUrl;

    /** 답장 대상 메시지 ID. */
    @Column(name = "REPLY_MESSAGE_ID")
    private Long replyMessageId;

    /** 전송 일시. */
    @Column(name = "SEND_DATE", nullable = false)
    private LocalDateTime sendDate = LocalDateTime.now();

    /**
     * 사용자가 보낸 메시지를 만든다.
     *
     * @param chatRoomId     채팅방 ID
     * @param senderUserId   발신자(인증 주체에서 가져온 값)
     * @param messageType    메시지 유형
     * @param content        내용
     * @param replyMessageId 답장 대상(선택)
     * @return 저장 대상 엔티티
     */
    public static ChatMessage of(Long chatRoomId, Long senderUserId, String messageType,
                                 String content, Long replyMessageId) {
        ChatMessage message = new ChatMessage();
        message.chatRoomId = chatRoomId;
        message.senderUserId = senderUserId;
        message.messageType = messageType;
        message.messageContent = content;
        message.replyMessageId = replyMessageId;
        message.sendDate = LocalDateTime.now();
        return message;
    }
}
