package com.bma.reveal.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.reveal.dto.RevealDtos.ConsentRequest;
import com.bma.reveal.dto.RevealDtos.ConsentResult;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.bma.reveal.dto.RevealDtos.RevealActionResponse;
import com.bma.reveal.dto.RevealDtos.RevealConsentRequest;
import com.bma.reveal.dto.RevealDtos.RevealStatusResponse;
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
 * Reveal API (S10, BMA-69). 매칭 상세 자체는 {@code GET /api/v1/matches/{matchId}} (MatchingController).
 */
@Tag(name = "Reveal", description = "블라인드 해제 단계 조회, 다음 단계 요청·동의")
@RestController
@RequestMapping("/api/v1/matches/{matchId}")
@RequiredArgsConstructor
public class RevealController {

    private final RevealService revealService;

    @Operation(summary = "공개 단계 상태 조회 (S10 진행바·칩·모달)",
            description = "시간·양측 메시지 조건, 요청 가능 여부(canRequest), 상대 요청 대기(incomingRequest). 구독 여부는 드러나지 않는다.")
    @GetMapping("/reveal")
    public ApiResponse<RevealStatusResponse> getStatus(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                       @PathVariable Long matchId) {
        return ApiResponse.ok(revealService.getStatus(principal.userId(), matchId));
    }

    @Operation(summary = "상대 프로필 조회", description = "현재 공개 단계에 허용된 항목만 반환한다(키는 부분 공개부터, 이름은 전체 공개부터).")
    @GetMapping("/reveal/partner")
    public ApiResponse<MaskedProfileResponse> getPartnerProfile(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                @PathVariable Long matchId) {
        return ApiResponse.ok(revealService.getPartnerProfile(principal.userId(), matchId));
    }

    @Operation(summary = "다음 단계 요청 (S10-12 칩)",
            description = "24시간 경과(구독자 스킵) + 양측 각자 10개 이상 메시지일 때만. 요청은 곧 내 동의이며 "
                    + "상대가 이미 동의했으면 바로 단계가 올라간다. 조건 미충족 409 REVEAL_002, 최고 단계 400 REVEAL_003.")
    @PostMapping("/reveal-request")
    public ApiResponse<RevealActionResponse> request(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                     @PathVariable Long matchId) {
        return ApiResponse.ok(revealService.request(principal.userId(), matchId));
    }

    @Operation(summary = "요청에 동의 (S10-16 동의하고 열기 / S10-17 나중에)",
            description = "consent=true 면 동의 → 양쪽 동의가 되어 단계 상승. false·생략은 '나중에'로 아무것도 기록하지 않는다(상대에게 비노출).")
    @PostMapping("/reveal-consent")
    public ApiResponse<RevealActionResponse> consent(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                     @PathVariable Long matchId,
                                                     @RequestBody(required = false) RevealConsentRequest request) {
        boolean accepted = request != null && request.isAccepted();
        return ApiResponse.ok(revealService.consent(principal.userId(), matchId, accepted));
    }

    @Operation(summary = "(구) 공개 단계 동의", description = "revealLevel 은 현재 단계 + 1 이어야 한다. reveal-request/reveal-consent 를 쓰는 것을 권장.")
    @PostMapping("/reveal/consent")
    public ApiResponse<ConsentResult> consentLegacy(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                    @PathVariable Long matchId,
                                                    @Valid @RequestBody ConsentRequest request) {
        return ApiResponse.ok(revealService.consentLegacy(principal.userId(), matchId, request));
    }
}
