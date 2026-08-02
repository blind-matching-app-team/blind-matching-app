package com.bma.matching.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.matching.dto.MatchingDtos.ActionRequest;
import com.bma.matching.dto.MatchingDtos.ActionResult;
import com.bma.matching.dto.MatchingDtos.MatchResponse;
import com.bma.matching.dto.MatchingDtos.QueueResponse;
import com.bma.matching.dto.MatchingDtos.RecommendationResponse;
import com.bma.matching.service.MatchingService;
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
 * 매칭 API.
 *
 * <p>기존에는 컨트롤러가 저장소를 직접 호출하고 {@code @Transactional}까지 붙어 있었다.
 * 업무 규칙은 모두 {@link MatchingService}로 옮기고 여기서는 요청/응답 변환만 담당한다.</p>
 */
@Tag(name = "Matching", description = "추천, 좋아요/패스, 매칭 관리")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    /**
     * 추천 목록 조회.
     *
     * @param principal    인증 주체
     * @param size         조회 건수
     * @param cursorScore  커서: 직전 페이지 마지막 완성도 점수
     * @param cursorUserId 커서: 직전 페이지 마지막 사용자 ID
     * @return 마스킹된 추천 목록
     */
    @Operation(summary = "추천 목록 조회",
            description = "선호 조건을 반영하며, 이미 액션한 상대·차단 상대·기존 매칭은 제외된다. "
                    + "프로필은 항상 공개 단계 0으로 마스킹된다.")
    @GetMapping("/matching/recommendations")
    public ApiResponse<List<RecommendationResponse>> recommendations(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer cursorScore,
            @RequestParam(required = false) Long cursorUserId) {
        return ApiResponse.ok(
                matchingService.getRecommendations(principal.userId(), size, cursorScore, cursorUserId));
    }

    /**
     * 추천 상대에 대한 액션(좋아요/슈퍼좋아요/패스).
     *
     * @param principal 인증 주체
     * @param request   액션 요청
     * @return 처리 결과(상호 매칭 성사 여부 포함)
     */
    @Operation(summary = "좋아요/패스",
            description = "상대도 호감을 표시한 상태라면 이 시점에 매칭이 성사되고 채팅방이 생성된다.")
    @PostMapping("/matching/actions")
    public ApiResponse<ActionResult> action(@AuthenticationPrincipal CustomUserPrincipal principal,
                                            @Valid @RequestBody ActionRequest request) {
        return ApiResponse.ok(matchingService.act(principal.userId(), request));
    }

    /**
     * 매칭 대기열 참여.
     *
     * @param principal 인증 주체
     * @return 대기열 상태
     */
    @Operation(summary = "매칭 대기열 참여")
    @PostMapping("/matching/queue")
    public ApiResponse<QueueResponse> joinQueue(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(matchingService.joinQueue(principal.userId()));
    }

    /**
     * 매칭 대기열 취소.
     *
     * @param principal 인증 주체
     * @return 빈 성공 응답
     */
    @Operation(summary = "매칭 대기열 취소")
    @DeleteMapping("/matching/queue")
    public ApiResponse<Void> cancelQueue(@AuthenticationPrincipal CustomUserPrincipal principal) {
        matchingService.cancelQueue(principal.userId());
        return ApiResponse.ok();
    }

    /**
     * 내 매칭 목록 조회.
     *
     * @param principal 인증 주체
     * @return 진행 중인 매칭 목록
     */
    @Operation(summary = "내 매칭 목록")
    @GetMapping("/matches")
    public ApiResponse<List<MatchResponse>> matches(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(matchingService.getMyMatches(principal.userId()));
    }

    /**
     * 매칭 해제.
     *
     * @param principal 인증 주체
     * @param matchId   매칭 ID
     * @return 빈 성공 응답
     */
    @Operation(summary = "매칭 해제", description = "연결된 채팅방도 함께 종료된다.")
    @DeleteMapping("/matches/{matchId}")
    public ApiResponse<Void> unmatch(@AuthenticationPrincipal CustomUserPrincipal principal,
                                     @PathVariable Long matchId) {
        matchingService.unmatch(principal.userId(), matchId);
        return ApiResponse.ok();
    }
}
