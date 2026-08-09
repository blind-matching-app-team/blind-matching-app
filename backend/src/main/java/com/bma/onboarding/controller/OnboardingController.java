package com.bma.onboarding.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.onboarding.dto.OnboardingDtos.AnswerRequest;
import com.bma.onboarding.dto.OnboardingDtos.AnswerResult;
import com.bma.onboarding.dto.OnboardingDtos.QuestionView;
import com.bma.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 온보딩(가치관 질문) API.
 */
@Tag(name = "Onboarding", description = "가치관 질문 조회 및 답변 제출")
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /**
     * 질문 목록 조회.
     *
     * @return 사용 중인 질문과 보기 목록
     */
    @Operation(summary = "온보딩 질문 목록")
    @GetMapping("/questions")
    public ApiResponse<List<QuestionView>> getQuestions() {
        return ApiResponse.ok(onboardingService.getQuestions());
    }

    /**
     * 답변 제출. 같은 질문에 다시 제출하면 기존 답변을 대체한다.
     *
     * @param principal 인증 주체
     * @param request   답변 요청
     * @return 저장 결과
     */
    @Operation(summary = "온보딩 답변 제출", description = "재제출 시 해당 질문의 기존 답변을 대체한다.")
    @PostMapping("/answers")
    public ApiResponse<AnswerResult> saveAnswers(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                 @Valid @RequestBody AnswerRequest request) {
        return ApiResponse.ok(onboardingService.saveAnswers(principal.userId(), request));
    }
}
