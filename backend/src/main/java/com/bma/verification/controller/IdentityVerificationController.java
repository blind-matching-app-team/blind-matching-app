package com.bma.verification.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.verification.dto.IdentityVerificationDtos.ConfirmRequest;
import com.bma.verification.dto.IdentityVerificationDtos.ConfirmResponse;
import com.bma.verification.dto.IdentityVerificationDtos.RequestResponse;
import com.bma.verification.dto.IdentityVerificationDtos.StatusResponse;
import com.bma.verification.service.IdentityVerificationService;
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

/**
 * S15 본인인증 API (BMA-79). S4 "매칭 시작하기" → 미인증이면 S15 → 인증 후 S9.
 */
@Tag(name = "Verification", description = "본인인증 (S15)")
@RestController
@RequestMapping("/api/v1/verification/identity")
@RequiredArgsConstructor
public class IdentityVerificationController {

    private final IdentityVerificationService service;

    @Operation(summary = "본인인증 상태", description = "verified=true 면 S15 를 건너뛰고 바로 S9 로 간다. GET /users/me 의 identityVerified 와 같다.")
    @GetMapping("/status")
    public ApiResponse<StatusResponse> status(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(service.status(principal.userId()));
    }

    @Operation(summary = "본인인증 요청 (S15-04)",
            description = "인증사 SDK 호출용 거래 ID·파라미터를 발급한다(10분 유효). 이미 완료한 계정은 409 VERIFY_002.")
    @PostMapping("/request")
    public ApiResponse<RequestResponse> request(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(service.request(principal.userId()));
    }

    @Operation(summary = "본인인증 결과 확인",
            description = "SDK 결과를 검증해 계정에 완료를 반영한다. 인증사 생년월일 기준 만 19세 미만이면 계정을 롤백하고 403 VERIFY_003(S15-06~09). "
                    + "같은 사람이 이미 다른 계정으로 인증했으면 409 VERIFY_004. 거래 ID 오류 404 VERIFY_005, 만료 409 VERIFY_006, 검증 실패 400 VERIFY_007.")
    @PostMapping("/confirm")
    public ApiResponse<ConfirmResponse> confirm(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                @Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.ok(service.confirm(principal.userId(), request));
    }
}
