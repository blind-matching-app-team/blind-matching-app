package com.bma.notification.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.response.PageResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.notification.dto.NotificationDtos.NotificationResponse;
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
 * 인앱 알림 API.
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
     * @param principal 인증 주체
     * @param page      페이지 번호
     * @param size      페이지 크기
     * @return 알림 페이지
     */
    @Operation(summary = "알림 목록")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.getNotifications(principal.userId(), page, size));
    }

    /**
     * 안 읽은 알림 수 조회.
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
     * @return 빈 성공 응답
     */
    @Operation(summary = "알림 읽음 처리")
    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal CustomUserPrincipal principal,
                                  @PathVariable Long id) {
        notificationService.markRead(principal.userId(), id);
        return ApiResponse.ok();
    }

    /**
     * 전체 읽음 처리.
     *
     * @param principal 인증 주체
     * @return 처리된 건수
     */
    @Operation(summary = "알림 전체 읽음 처리")
    @PutMapping("/read-all")
    public ApiResponse<Integer> readAll(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(notificationService.markAllRead(principal.userId()));
    }
}
