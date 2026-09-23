package com.bma.chat.dto;

import com.bma.chat.entity.ChatMessage;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
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
     * 전송 처리 결과. 컨트롤러가 어디로 배달할지 정하는 데 쓴다(응답 본문에는 {@code message}만 나간다).
     *
     * @param message 저장된 메시지
     * @param hidden  차단 상대에게 보낸 숨김 메시지인지. {@code true}면 방에 브로드캐스트하지 않고 발신자에게만 되돌린다(S11-08)
     */
    public record SendResult(MessageResponse message, boolean hidden) {
    }

    /**
     * 채팅방 목록 항목 (S6-09 리스트 아이템).
     *
     * <p>아이템 하나를 그리는 데 필요한 것을 전부 담는다: 상대의 마스킹된 프로필(블러 아바타·"???"),
     * 마지막 메시지 미리보기와 시각, 안읽음 배지, 종료됨 배지(S6-12)용 상태.</p>
     *
     * @param chatRoomId      채팅방 ID. 클릭 시 S11 로 넘긴다
     * @param matchId         매칭 ID
     * @param partnerUserId   상대 사용자 ID
     * @param status          {@code ACTIVE}(대화 가능) 또는 {@code ENDED}(매칭 종료, 읽기 전용)
     * @param partner         상대 프로필(현재 공개 단계로 마스킹). 상대가 탈퇴 등으로 없으면 {@code null}
     * @param lastMessage     마지막 메시지 미리보기(최대 50자). 메시지가 없으면 {@code null}
     * @param lastMessageType 마지막 메시지 유형(TEXT/IMAGE/SYSTEM/REVEAL). 없으면 {@code null}
     * @param lastMessageDate 마지막 메시지 일시. 없으면 {@code null}
     * @param unreadCount     안 읽은 메시지 수. 종료된 방은 항상 0
     * @param revealLevel     현재 공개 단계
     */
    // 미리보기·상대 정보가 없을 때도 키를 남긴다. 프론트가 아이템에 그대로 바인딩한다.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChatRoomResponse(Long chatRoomId,
                                   Long matchId,
                                   Long partnerUserId,
                                   String status,
                                   MaskedProfileResponse partner,
                                   String lastMessage,
                                   String lastMessageType,
                                   LocalDateTime lastMessageDate,
                                   long unreadCount,
                                   Integer revealLevel) {
    }

    /**
     * 안 읽은 메시지 합계 (사이드바 채팅 배지).
     *
     * <p>목록 API 의 {@code unreadCount} 합과 같다. 종료된 방은 세지 않는다.</p>
     *
     * @param unreadCount 합계
     */
    public record UnreadCountResponse(long unreadCount) {
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
