package com.bma.user.controller;
import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.user.dto.UserDtos.MeResponse;
import com.bma.user.dto.UserDtos.NicknameCheckResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


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
     * 닉네임 사용 가능 여부 확인 (S3-06).
     *
     * <p>저장 시점에도 같은 검사를 하므로 이 결과가 저장을 보장하지는 않는다.
     * 확인과 저장 사이에 다른 사용자가 같은 닉네임을 선점할 수 있다.</p>
     *
     * @param principal 인증 주체
     * @param nickname  확인할 닉네임
     * @return 사용 가능 여부
     */
    @Operation(summary = "닉네임 사용 가능 여부 확인")
    @GetMapping("/profile/nickname-check")
    public ApiResponse<NicknameCheckResponse> checkNickname(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam String nickname) {
        return ApiResponse.ok(userService.checkNickname(principal.userId(), nickname));
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
     * 내 프로필 이미지 조회.
     *
     * @param principal 인증 주체
     * @return 등록된 이미지. 아직 없으면 data 가 null 이다
     */
    @Operation(summary = "내 프로필 이미지 조회")
    @GetMapping("/image")
    public ApiResponse<ProfileImageResponse> getImage(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        // 사진 미등록은 오류가 아니라 정상 상태다. 404 대신 빈 값을 준다.
        return ApiResponse.ok(userService.getImage(principal.userId()).orElse(null));
    }

    /**
     * 프로필 이미지 업로드. 이미 있으면 교체한다.
     *
     * @param principal 인증 주체
     * @param file      이미지 파일
     * @return 저장된 이미지 정보
     */
    @Operation(summary = "프로필 이미지 업로드",
            description = "사진은 1장만 갖는다. 이미 있으면 교체한다. MIME 타입과 파일 시그니처를 모두 검증한다.")
    @PostMapping("/image")
    public ApiResponse<ProfileImageResponse> uploadImage(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userService.saveImage(principal.userId(), file));
    }

    /**
     * 프로필 이미지 삭제. 기본 아바타로 돌아간다.
     *
     * @param principal 인증 주체
     * @return 빈 성공 응답
     */
    @Operation(summary = "프로필 이미지 삭제")
    @DeleteMapping("/image")
    public ApiResponse<Void> deleteImage(@AuthenticationPrincipal CustomUserPrincipal principal) {
        userService.deleteImage(principal.userId());
        return ApiResponse.ok();
    }

    // 이미지 순서 변경 / 대표 지정 엔드포인트는 제거했다. 사진이 1장뿐이라
    // 순서를 매길 대상도, 여러 장 중 대표를 고를 대상도 없다.
}
