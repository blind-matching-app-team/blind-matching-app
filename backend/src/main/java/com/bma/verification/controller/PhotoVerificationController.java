package com.bma.verification.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.verification.dto.PhotoVerificationDtos.StatusResponse;
import com.bma.verification.dto.PhotoVerificationDtos.VerifyResponse;
import com.bma.verification.service.PhotoVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * S16 사진인증 API (BMA-82). S8-17 "사진인증 받기" → S16 촬영 → 대조 → 배지.
 */
@Tag(name = "Verification", description = "사진인증 (S16)")
@RestController
@RequestMapping("/api/v1/verification/photo")
@RequiredArgsConstructor
public class PhotoVerificationController {

    private final PhotoVerificationService service;

    @Operation(summary = "사진인증 상태", description = "verified=true 면 S8-17 은 '인증완료'로 표시하고 S16 에 재진입하지 않는다.")
    @GetMapping("/status")
    public ApiResponse<StatusResponse> status(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(service.status(principal.userId()));
    }

    @Operation(summary = "촬영 이미지 대조 (S16-04)",
            description = "multipart file 로 셀피를 보내면 프로필 사진과 대조한다. 셀피는 대조 즉시 폐기되어 저장되지 않는다. "
                    + "통과 → 200(배지 부여). 불일치·얼굴 미검출 → 422 VERIFY_011(다시 촬영, 횟수 제한 없음). "
                    + "프로필 사진 없음 409 VERIFY_010, 이미 완료 409 VERIFY_012, 제공자 오류 502 VERIFY_013, 이미지 아님 400 USER_005.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VerifyResponse> verify(@AuthenticationPrincipal CustomUserPrincipal principal,
                                              @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.verify(principal.userId(), file));
    }
}
