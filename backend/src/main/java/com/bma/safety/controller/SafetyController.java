package com.bma.safety.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.safety.dto.SafetyDtos.BlockRequest;
import com.bma.safety.dto.SafetyDtos.BlockResult;
import com.bma.safety.dto.SafetyDtos.BlockedUserResponse;
import com.bma.safety.dto.SafetyDtos.CreateReportRequest;
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
 * 차단 및 신고 API (BMA-30 정책, S11-09~12).
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
    @Operation(summary = "사용자 차단 (S11-09 차단하기)",
            description = "차단자만 채팅방에서 나가고 상대는 아무 변화가 없다(차단 사실 비노출). 이후 상대가 보내는 메시지는 "
                    + "상대 화면에는 정상 전송처럼 보이지만 전달되지 않으며, 매칭 알고리즘에서 서로 영구 제외된다.")
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
     * 신고 제출 (S11-12).
     *
     * @param principal 인증 주체
     * @param request   신고 내용(피신고자 포함)
     * @return 접수 결과
     */
    @Operation(summary = "신고 제출 (S11-10~12, BMA-30 정책)",
            description = "유형 5종(ABUSE 욕설/FAKE 허위프로필/FRAUD 사기/SEXUAL 부적절한 콘텐츠/ETC 기타). "
                    + "사기·부적절한 콘텐츠는 1회로 즉시 관리자 검토(PENDING_REVIEW, immediateReview=true)이고 그동안 피신고자는 새 매칭에 못 들어간다. "
                    + "일반 유형은 누적 1~2회 자동 반영(COUNTED), 3회째부터 관리자 검토. 같은 매칭에 대한 재신고는 409 SAFE_001. "
                    + "신고자만 채팅방에서 나가고 상대는 무변화.")
    @PostMapping("/reports")
    public ApiResponse<ReportResponse> createReport(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                    @Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.ok(safetyService.report(principal.userId(), request.toReportRequest()));
    }

    /**
     * 사용자 신고 (구 경로, 호환용).
     *
     * @param principal 인증 주체
     * @param id        피신고자 ID
     * @param request   신고 내용
     * @return 접수 결과
     */
    @Operation(summary = "(구) 사용자 신고", description = "POST /reports 와 같은 처리. 피신고자는 경로값을 쓴다.")
    @PostMapping("/users/{id}/report")
    public ApiResponse<ReportResponse> report(@AuthenticationPrincipal CustomUserPrincipal principal,
                                              @PathVariable Long id,
                                              @Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok(safetyService.report(principal.userId(), request.withTarget(id)));
    }
}
