package com.bma.safety.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.safety.dto.SafetyDtos.BlockRequest;
import com.bma.safety.dto.SafetyDtos.BlockResult;
import com.bma.safety.dto.SafetyDtos.BlockedUserResponse;
import com.bma.safety.dto.SafetyDtos.ReportRequest;
import com.bma.safety.dto.SafetyDtos.ReportResponse;
import com.bma.safety.service.SafetyService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 차단 및 신고 API.
 */
@Tag(name = "Safety", description = "사용자 차단 및 신고")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    /**
     * 사용자 차단.
     *
     * @param principal 인증 주체
     * @param id        차단 대상 사용자 ID
     * @param request   차단 사유(선택)
     * @return 처리 결과
     */
    @Operation(summary = "사용자 차단", description = "진행 중이던 매칭이 있으면 함께 종료된다.")
    @PostMapping("/users/{id}/block")
    public ApiResponse<BlockResult> block(@AuthenticationPrincipal CustomUserPrincipal principal,
                                          @PathVariable Long id,
                                          @Valid @RequestBody(required = false) BlockRequest request) {
        String reason = (request == null) ? null : request.reason();
        return ApiResponse.ok(safetyService.block(principal.userId(), id, reason));
    }

    /**
     * 차단 해제.
     *
     * @param principal 인증 주체
     * @param id        대상 사용자 ID
     * @return 처리 결과
     */
    @Operation(summary = "차단 해제")
    @DeleteMapping("/users/{id}/block")
    public ApiResponse<BlockResult> unblock(@AuthenticationPrincipal CustomUserPrincipal principal,
                                            @PathVariable Long id) {
        return ApiResponse.ok(safetyService.unblock(principal.userId(), id));
    }

    /**
     * 내가 차단한 사용자 목록.
     *
     * @param principal 인증 주체
     * @return 차단 목록
     */
    @Operation(summary = "차단 목록 조회")
    @GetMapping("/users/me/blocks")
    public ApiResponse<List<BlockedUserResponse>> blockedUsers(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(safetyService.getBlockedUsers(principal.userId()));
    }

    /**
     * 사용자 신고.
     *
     * @param principal 인증 주체
     * @param id        피신고자 ID
     * @param request   신고 내용
     * @return 접수 결과
     */
    @Operation(summary = "사용자 신고", description = "동일 대상에 대한 24시간 내 중복 신고는 거부된다.")
    @PostMapping("/users/{id}/report")
    public ApiResponse<ReportResponse> report(@AuthenticationPrincipal CustomUserPrincipal principal,
                                              @PathVariable Long id,
                                              @Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok(safetyService.report(principal.userId(), id, request));
    }
}
