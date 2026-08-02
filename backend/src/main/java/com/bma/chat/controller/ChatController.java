package com.bma.chat.controller;

import com.bma.chat.dto.ChatDtos.ChatRoomResponse;
import com.bma.chat.dto.ChatDtos.MessageResponse;
import com.bma.chat.dto.ChatDtos.ReadResult;
import com.bma.chat.service.ChatService;
import com.bma.common.response.ApiResponse;
import com.bma.common.response.PageResponse;
import com.bma.common.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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

    /**
     * 내 채팅방 목록 조회.
     *
     * @param principal 인증 주체
     * @return 참여 중인 채팅방 목록
     */
    @Operation(summary = "내 채팅방 목록", description = "본인이 참여 중인 방만 반환한다.")
    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomResponse>> rooms(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(chatService.getMyRooms(principal.userId()));
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
