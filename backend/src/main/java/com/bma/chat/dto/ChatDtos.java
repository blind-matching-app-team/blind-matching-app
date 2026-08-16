package com.bma.chat.dto;

import com.bma.chat.entity.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 채팅 API의 요청/응답 DTO 모음.
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    /**
     * 메시지 전송 요청(STOMP 페이로드).
     *
     * <p><b>중요</b>: 기존 페이로드에는 {@code senderId} 필드가 있어 클라이언트가
     * 발신자를 지정할 수 있었다. 이 DTO에는 그 필드가 없다.
     * 발신자는 오직 인증된 세션에서만 결정된다.</p>
     *
     * @param messageType    메시지 유형(TEXT/IMAGE). 생략하면 TEXT
     * @param content        메시지 내용
     * @param replyMessageId 답장 대상 메시지 ID(선택)
     */
    public record SendMessageRequest(
            @Pattern(regexp = "TEXT|IMAGE", message = "메시지 유형은 TEXT 또는 IMAGE 여야 합니다.")
            String messageType,

            @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
            @Size(max = 4000, message = "메시지는 4000자를 넘을 수 없습니다.")
            String content,

            Long replyMessageId
    ) {
    }

    /**
     * 메시지 응답.
     *
     * @param messageId      메시지 ID
     * @param chatRoomId     채팅방 ID
     * @param senderUserId   발신자 ID
     * @param messageType    메시지 유형
     * @param content        내용
     * @param replyMessageId 답장 대상
     * @param sendDate       전송 일시
     */
    public record MessageResponse(Long messageId,
                                  Long chatRoomId,
                                  Long senderUserId,
                                  String messageType,
                                  String content,
                                  Long replyMessageId,
                                  LocalDateTime sendDate) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param message 메시지 엔티티
         * @return 응답 DTO
         */
        public static MessageResponse from(ChatMessage message) {
            return new MessageResponse(
                    message.getId(),
                    message.getChatRoomId(),
                    message.getSenderUserId(),
                    message.getMessageType(),
                    message.getMessageContent(),
                    message.getReplyMessageId(),
                    message.getSendDate());
        }
    }

    /**
     * 채팅방 목록 항목.
     *
     * @param chatRoomId      채팅방 ID
     * @param matchId         매칭 ID
     * @param partnerUserId   상대 사용자 ID
     * @param roomStatus      방 상태
     * @param lastMessageDate 마지막 메시지 일시
     * @param unreadCount     안 읽은 메시지 수
     * @param revealLevel     현재 공개 단계
     */
    public record ChatRoomResponse(Long chatRoomId,
                                   Long matchId,
                                   Long partnerUserId,
                                   String roomStatus,
                                   LocalDateTime lastMessageDate,
                                   long unreadCount,
                                   Integer revealLevel) {
    }

    /**
     * 읽음 처리 요청 결과.
     *
     * @param chatRoomId        채팅방 ID
     * @param lastReadMessageId 갱신된 읽음 위치
     */
    public record ReadResult(Long chatRoomId, Long lastReadMessageId) {
    }
}
