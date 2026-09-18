package com.bma.notification.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.response.PageResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.notification.dto.NotificationDtos.NotificationResponse;
import com.bma.notification.dto.NotificationDtos.ReadAllResult;
import com.bma.notification.dto.NotificationDtos.ReadResult;
import com.bma.notification.dto.NotificationDtos.UnreadCountResponse;
import com.bma.notification.service.NotificationService;
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

/**
 * 인앱 알림 API (S7).
 *
 * <p>모든 조회/수정은 토큰 주체의 알림으로 한정된다.</p>
 */
@Tag(name = "Notification", description = "인앱 알림")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 알림 목록 조회.
     *
     * @param principal  인증 주체
     * @param page       페이지 번호
     * @param size       페이지 크기
     * @param unreadOnly 안 읽은 알림만 볼지
     * @return 알림 페이지(최신순)
     */
    @Operation(summary = "알림 목록 (S7)",
            description = "최신순 페이지. 각 항목에 사건 코드(eventCode)와 클릭 이동 정보(target)가 있다. unreadOnly=true 면 안 읽은 것만.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.ok(notificationService.getNotifications(principal.userId(), page, size, unreadOnly));
    }

    /**
     * 안 읽은 알림 수 조회 (사이드바 알림 배지).
     *
     * @param principal 인증 주체
     * @return 안 읽은 알림 수
     */
    @Operation(summary = "안 읽은 알림 수")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(new UnreadCountResponse(notificationService.countUnread(principal.userId())));
    }

    /**
     * 알림 1건 읽음 처리.
     *
     * @param principal 인증 주체
     * @param id        알림 ID
     * @return 읽은 시각과 남은 안 읽은 수
     */
    @Operation(summary = "알림 읽음 처리", description = "본인 알림만. 이미 읽은 알림이면 그대로 200(멱등). 남의 알림은 404.")
    @PutMapping("/{id}/read")
    public ApiResponse<ReadResult> read(@AuthenticationPrincipal CustomUserPrincipal principal,
                                        @PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(principal.userId(), id));
    }

    /**
     * 전체 읽음 처리 (S7-08).
     *
     * @param principal 인증 주체
     * @return 바뀐 건수와 남은 안 읽은 수(0)
     */
    @Operation(summary = "알림 전체 읽음 처리 (S7-08)")
    @PutMapping("/read-all")
    public ApiResponse<ReadAllResult> readAll(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(notificationService.markAllRead(principal.userId()));
    }
}
