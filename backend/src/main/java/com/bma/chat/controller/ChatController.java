package com.bma.chat.controller;

import com.bma.chat.dto.ChatDtos.ChatRoomResponse;
import com.bma.chat.dto.ChatDtos.MessageResponse;
import com.bma.chat.dto.ChatDtos.ReadResult;
import com.bma.chat.dto.ChatDtos.SendMessageRequest;
import com.bma.chat.dto.ChatDtos.UnreadCountResponse;
import com.bma.chat.service.ChatService;
import com.bma.common.response.ApiResponse;
import com.bma.common.response.PageResponse;
import com.bma.common.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅 REST API.
 *
 * <p>모든 엔드포인트는 요청자가 해당 채팅방의 참여자인지 확인한 뒤 동작한다.</p>
 */
@Tag(name = "Chat", description = "채팅방 및 메시지 이력")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 내 채팅방 목록 조회.
     *
     * @param principal 인증 주체
     * @return 참여 중인 채팅방 목록
     */
    @Operation(summary = "내 채팅방 목록 (S6)",
            description = "본인이 참여 중인 방을 최근 메시지순으로 반환한다. 매칭이 종료된 방도 status=ENDED 로 남는다(읽기 전용). "
                    + "각 항목에 상대의 마스킹된 프로필, 마지막 메시지 미리보기, 안읽음 수가 포함된다. 없으면 빈 배열.")
    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomResponse>> rooms(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(chatService.getMyRooms(principal.userId()));
    }

    /**
     * 안 읽은 메시지 합계 조회 (사이드바 채팅 배지).
     *
     * @param principal 인증 주체
     * @return 합계. 종료된 방은 세지 않는다
     */
    @Operation(summary = "안 읽은 메시지 합계", description = "목록 API 의 unreadCount 합과 항상 같다. 종료된 방은 제외.")
    @GetMapping("/rooms/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(new UnreadCountResponse(chatService.countUnreadTotal(principal.userId())));
    }

    /**
     * 채팅방 나가기 (S6-11).
     *
     * @param principal 인증 주체
     * @param roomId    채팅방 ID
     * @return 빈 성공 응답
     */
    @Operation(summary = "채팅방 나가기 (S6-11)",
            description = "내 목록에서만 빠진다. 상대 목록에는 남고 메시지도 지워지지 않는다. "
                    + "상대가 새 메시지를 보내면 방이 다시 나타난다.")
    @DeleteMapping("/rooms/{roomId}")
    public ApiResponse<Void> leave(@AuthenticationPrincipal CustomUserPrincipal principal,
                                   @PathVariable Long roomId) {
        chatService.leaveRoom(principal.userId(), roomId);
        return ApiResponse.ok();
    }

    /**
     * 메시지 전송 (REST).
     *
     * <p>실시간 경로는 STOMP {@code /app/chat/{roomId}/send} 다. 이 엔드포인트는 같은 처리를
     * REST 로 노출한 것으로, 저장 후 같은 토픽에 브로드캐스트하므로 구독 중인 클라이언트도 받는다.</p>
     *
     * @param principal 인증 주체
     * @param roomId    채팅방 ID
     * @param request   전송 요청
     * @return 저장된 메시지
     */
    @Operation(summary = "메시지 전송 (REST)",
            description = "STOMP 전송과 같은 처리. 저장 후 /topic/chat/{roomId} 로 브로드캐스트한다. 종료된 방이면 409.")
    @PostMapping("/rooms/{roomId}/messages")
    public ApiResponse<MessageResponse> send(@AuthenticationPrincipal CustomUserPrincipal principal,
                                             @PathVariable Long roomId,
                                             @Valid @RequestBody SendMessageRequest request) {
        MessageResponse saved = chatService.sendMessage(principal.userId(), roomId, request);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, saved);
        return ApiResponse.ok(saved);
    }

    /**
     * 채팅방 메시지 이력 조회.
     *
     * @param principal 인증 주체
     * @param roomId    채팅방 ID
     * @param page      페이지 번호
     * @param size      페이지 크기
     * @return 메시지 페이지(최신순)
     */
    @Operation(summary = "메시지 이력 조회")
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<PageResponse<MessageResponse>> history(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(chatService.getMessages(principal.userId(), roomId, page, size));
    }

    /**
     * 읽음 위치 갱신.
     *
     * @param principal 인증 주체
     * @param roomId    채팅방 ID
     * @param messageId 마지막으로 읽은 메시지 ID
     * @return 갱신 결과
     */
    @Operation(summary = "읽음 처리")
    @PutMapping("/rooms/{roomId}/read")
    public ApiResponse<ReadResult> read(@AuthenticationPrincipal CustomUserPrincipal principal,
                                        @PathVariable Long roomId,
                                        @RequestParam Long messageId) {
        return ApiResponse.ok(chatService.markRead(principal.userId(), roomId, messageId));
    }
}
