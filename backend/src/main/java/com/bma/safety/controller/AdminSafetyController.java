package com.bma.safety.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.response.PageResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.safety.dto.AdminSafetyDtos.AdminReportDetail;
import com.bma.safety.dto.AdminSafetyDtos.AdminReportSummary;
import com.bma.safety.dto.AdminSafetyDtos.AuditLogResponse;
import com.bma.safety.dto.AdminSafetyDtos.ReviewRequest;
import com.bma.safety.dto.AdminSafetyDtos.ReviewResponse;
import com.bma.safety.dto.AdminSafetyDtos.SanctionRequest;
import com.bma.safety.dto.AdminSafetyDtos.SanctionResponse;
import com.bma.safety.service.ReportReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * S12 관리자 신고검토 API (BMA-76). {@code /api/v1/admin/**} 는 SecurityConfig 에서 ROLE_ADMIN 을 요구한다.
 *
 * <p>관리자 구별: 일반 로그인(S1)과 같은 토큰을 쓰되 {@code US_USER.USER_ROLE='ADMIN'} 인 계정만 통과한다.
 * 역할은 JWT 클레임에 실리므로 승격 후 다시 로그인해야 한다. 승격 API 는 두지 않는다(운영 DB 작업).</p>
 */
@Tag(name = "Admin/Safety", description = "신고 검토·제재·감사 로그 (S12)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSafetyController {

    private final ReportReviewService reviewService;

    @Operation(summary = "신고 목록 (S12 탭)",
            description = "status=PENDING(검토 대기, 기본)/DONE(처리완료)/ALL. 각 항목에 신고자·피신고자(누적 카운트, 효력 있는 제재)·근거 메시지 미리보기.")
    @GetMapping("/reports")
    public ApiResponse<PageResponse<AdminReportSummary>> reports(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.list(status, page, size));
    }

    @Operation(summary = "신고 상세", description = "피신고자의 다른 신고 이력·제재 이력과 이 신고의 감사 로그(S12-08)를 함께 준다.")
    @GetMapping("/reports/{reportId}")
    public ApiResponse<AdminReportDetail> report(@PathVariable Long reportId) {
        return ApiResponse.ok(reviewService.get(reportId));
    }

    @Operation(summary = "승인/반려 (S12-06/07)",
            description = "APPROVE: 유효 판정 → 누적 반영 → BMA-30 단계(3회 경고·5회 7일 제한·7회 영구 차단) 또는 action 으로 지정한 조치 실행 → "
                    + "신고자와의 매칭·채팅방을 '관리자에 의해 종료'로 끝내고 피신고자에게 안내. REJECT: 누적 미반영, 즉시검토 매칭 제한 해제. "
                    + "검토 대기가 아닌 신고는 409 SAFE_002.")
    @PostMapping("/reports/{reportId}/review")
    public ApiResponse<ReviewResponse> review(@AuthenticationPrincipal CustomUserPrincipal principal,
                                              @PathVariable Long reportId,
                                              @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewService.review(principal.userId(), reportId, request));
    }

    @Operation(summary = "사용자 제재 이력")
    @GetMapping("/users/{userId}/sanctions")
    public ApiResponse<List<SanctionResponse>> sanctions(@PathVariable Long userId) {
        return ApiResponse.ok(reviewService.sanctions(userId));
    }

    @Operation(summary = "직권 제재", description = "누적 대기 없이 WARNING/SUSPEND(days, 기본 7)/BAN 을 즉시 부과한다. 감사 로그에 남는다.")
    @PostMapping("/users/{userId}/sanctions")
    public ApiResponse<SanctionResponse> sanction(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                  @PathVariable Long userId,
                                                  @Valid @RequestBody SanctionRequest request) {
        return ApiResponse.ok(reviewService.sanction(principal.userId(), userId, request));
    }

    @Operation(summary = "제재 해제", description = "로그인을 막는 제재가 더 없으면 계정이 다시 활성화된다.")
    @DeleteMapping("/sanctions/{sanctionId}")
    public ApiResponse<SanctionResponse> lift(@AuthenticationPrincipal CustomUserPrincipal principal,
                                              @PathVariable Long sanctionId) {
        return ApiResponse.ok(reviewService.lift(principal.userId(), sanctionId));
    }

    @Operation(summary = "감사 로그 (S12-08)", description = "누가/언제/무슨 근거/무슨 조치. targetUserId·reportId 로 좁힐 수 있다. 최신순.")
    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> auditLogs(
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) Long reportId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(reviewService.auditLogs(targetUserId, reportId, page, size));
    }
}
