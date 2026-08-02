package com.bma.user.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.user.dto.UserDtos.ImageOrderRequest;
import com.bma.user.dto.UserDtos.MeResponse;
import com.bma.user.dto.UserDtos.PreferenceRequest;
import com.bma.user.dto.UserDtos.PreferenceResponse;
import com.bma.user.dto.UserDtos.ProfileImageResponse;
import com.bma.user.dto.UserDtos.ProfileRequest;
import com.bma.user.dto.UserDtos.ProfileResponse;
import com.bma.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 내 정보 API.
 *
 * <p>모든 엔드포인트는 인증된 사용자 본인의 데이터만 다룬다.
 * 경로에 사용자 ID를 받지 않고 토큰의 주체를 사용하므로 IDOR이 발생할 수 없다.</p>
 */
@Tag(name = "User", description = "내 프로필 및 이미지 관리")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 내 계정 정보 조회.
     *
     * @param principal 인증 주체
     * @return 계정 정보
     */
    @Operation(summary = "내 계정 정보 조회")
    @GetMapping
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(userService.getMe(principal.userId()));
    }

    /**
     * 내 프로필 조회.
     *
     * @param principal 인증 주체
     * @return 프로필
     */
    @Operation(summary = "내 프로필 조회")
    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(userService.getProfile(principal.userId()));
    }

    /**
     * 내 프로필 등록/수정.
     *
     * @param principal 인증 주체
     * @param request   프로필 요청
     * @return 저장된 프로필
     */
    @Operation(summary = "내 프로필 등록/수정")
    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> saveProfile(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                    @Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok(userService.saveProfile(principal.userId(), request));
    }

    /**
     * 내 선호 조건 조회.
     *
     * @param principal 인증 주체
     * @return 선호 조건
     */
    @Operation(summary = "내 선호 조건 조회")
    @GetMapping("/preference")
    public ApiResponse<PreferenceResponse> getPreference(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(userService.getPreference(principal.userId()));
    }

    /**
     * 내 선호 조건 등록/수정.
     *
     * @param principal 인증 주체
     * @param request   선호 조건 요청
     * @return 저장된 선호 조건
     */
    @Operation(summary = "내 선호 조건 등록/수정")
    @PutMapping("/preference")
    public ApiResponse<PreferenceResponse> savePreference(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                          @Valid @RequestBody PreferenceRequest request) {
        return ApiResponse.ok(userService.savePreference(principal.userId(), request));
    }

    /**
     * 내 프로필 이미지 목록 조회.
     *
     * @param principal 인증 주체
     * @return 이미지 목록
     */
    @Operation(summary = "내 프로필 이미지 목록")
    @GetMapping("/images")
    public ApiResponse<List<ProfileImageResponse>> getImages(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(userService.getImages(principal.userId()));
    }

    /**
     * 프로필 이미지 업로드.
     *
     * @param principal 인증 주체
     * @param file      이미지 파일(JPEG/PNG/WEBP)
     * @return 저장된 이미지 정보
     */
    @Operation(summary = "프로필 이미지 업로드", description = "MIME 타입과 파일 시그니처를 모두 검증한다.")
    @PostMapping("/images")
    public ApiResponse<ProfileImageResponse> uploadImage(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userService.uploadImage(principal.userId(), file));
    }

    /**
     * 프로필 이미지 삭제.
     *
     * @param principal 인증 주체
     * @param id        이미지 ID
     * @return 빈 성공 응답
     */
    @Operation(summary = "프로필 이미지 삭제")
    @DeleteMapping("/images/{id}")
    public ApiResponse<Void> deleteImage(@AuthenticationPrincipal CustomUserPrincipal principal,
                                         @PathVariable Long id) {
        userService.deleteImage(principal.userId(), id);
        return ApiResponse.ok();
    }

    /**
     * 프로필 이미지 순서/대표 변경.
     *
     * @param principal 인증 주체
     * @param request   변경 요청
     * @return 빈 성공 응답
     */
    @Operation(summary = "프로필 이미지 순서 변경")
    @PutMapping("/images/order")
    public ApiResponse<Void> reorderImages(@AuthenticationPrincipal CustomUserPrincipal principal,
                                           @Valid @RequestBody ImageOrderRequest request) {
        userService.reorderImages(principal.userId(), request);
        return ApiResponse.ok();
    }
}
