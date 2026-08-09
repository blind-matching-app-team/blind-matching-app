package com.bma.reveal.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.reveal.dto.RevealDtos.ConsentRequest;
import com.bma.reveal.dto.RevealDtos.ConsentResult;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.bma.reveal.dto.RevealDtos.RevealProgressResponse;
import com.bma.reveal.service.RevealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블라인드 해제(Reveal) API.
 *
 * <p>모든 엔드포인트는 요청자가 해당 매칭의 참여자인지 먼저 확인한다.</p>
 */
@Tag(name = "Reveal", description = "블라인드 해제 단계 조회 및 동의")
@RestController
@RequestMapping("/api/v1/matches/{matchId}/reveal")
@RequiredArgsConstructor
public class RevealController {

    private final RevealService revealService;

    /**
     * 공개 단계 진행 상태 조회.
     *
     * @param principal 인증 주체
     * @param matchId   매칭 ID
     * @return 진행 상태와 다음 단계 조건
     */
    @Operation(summary = "공개 단계 진행 상태 조회")
    @GetMapping
    public ApiResponse<RevealProgressResponse> getProgress(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long matchId) {
        return ApiResponse.ok(revealService.getProgress(principal.userId(), matchId));
    }

    /**
     * 현재 공개 단계에 맞춘 상대 프로필 조회.
     *
     * @param principal 인증 주체
     * @param matchId   매칭 ID
     * @return 마스킹된 상대 프로필
     */
    @Operation(summary = "상대 프로필 조회", description = "현재 공개 단계에 허용된 항목만 반환한다.")
    @GetMapping("/partner")
    public ApiResponse<MaskedProfileResponse> getPartnerProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long matchId) {
        return ApiResponse.ok(revealService.getPartnerProfile(principal.userId(), matchId));
    }

    /**
     * 다음 공개 단계 동의.
     *
     * @param principal 인증 주체
     * @param matchId   매칭 ID
     * @param request   동의 요청
     * @return 처리 결과
     */
    @Operation(summary = "공개 단계 동의",
            description = "대화량 조건을 충족해야 하며, 정책에 따라 상호 동의가 필요할 수 있다.")
    @PostMapping("/consent")
    public ApiResponse<ConsentResult> consent(@AuthenticationPrincipal CustomUserPrincipal principal,
                                              @PathVariable Long matchId,
                                              @Valid @RequestBody ConsentRequest request) {
        return ApiResponse.ok(revealService.consent(principal.userId(), matchId, request));
    }
}
