package com.bma.user.service;

import com.bma.common.entity.Region;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.service.RegionService;
import com.bma.storage.StorageService;
import com.bma.user.dto.UserDtos.MeResponse;
import com.bma.user.dto.UserDtos.NicknameCheckResponse;
import com.bma.user.dto.UserDtos.PreferenceRequest;
import com.bma.user.dto.UserDtos.PreferenceResponse;
import com.bma.user.dto.UserDtos.ProfileImageResponse;
import com.bma.user.dto.UserDtos.ProfileRequest;
import com.bma.user.dto.UserDtos.ProfileResponse;
import com.bma.user.entity.ProfileImage;
import com.bma.user.entity.User;
import com.bma.user.entity.UserPreference;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.ProfileImageRepository;
import com.bma.user.repository.UserPreferenceRepository;
import com.bma.user.repository.UserProfileRepository;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;


/**
 * 회원 프로필 / 선호 조건 / 프로필 이미지 관리.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li>엔티티 대신 DTO를 반환해 비밀번호 해시 등 내부 필드 노출을 차단했다.</li>
 *   <li>닉네임 중복을 사전 확인해 유니크 제약 위반으로 인한 500을 없앴다.</li>
 *   <li>프로필 사진은 1장만 갖는다. 다시 올리면 추가가 아니라 교체다.</li>
 *   <li>이미지 삭제 시 저장소 파일까지 함께 정리한다(기존에는 DB 플래그만 바꿔 파일이 계속 쌓였다).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final ProfileImageRepository imageRepository;
    private final RegionService regionService;
    private final StorageService storageService;

    /**
     * 내 계정 정보를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 계정 정보
     * @throws BusinessException 사용자를 찾을 수 없는 경우
     */
    public MeResponse getMe(Long userId) {
        User user = userRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MeResponse.from(user);
    }

    /**
     * 내 프로필을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 프로필
     * @throws BusinessException 프로필이 아직 없는 경우
     */
    public ProfileResponse getProfile(Long userId) {
        return profileRepository.findByIdAndDeleted(userId, YesNo.N)
                .map(this::toProfileResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
    }

    /**
     * 닉네임을 쓸 수 있는지 확인한다.
     *
     * <p>저장 시점에도 같은 검사를 하지만(그 사이에 남이 선점할 수 있다), 화면에서
     * 입력 도중 알려 주려면 별도 확인이 필요하다. 본인이 이미 쓰고 있는 닉네임은
     * 사용 가능으로 본다.</p>
     *
     * @param userId   사용자 ID
     * @param nickname 확인할 닉네임
     * @return 사용 가능 여부
     */
    public NicknameCheckResponse checkNickname(Long userId, String nickname) {
        String normalized = emptyToNull(nickname);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "닉네임을 입력해 주세요.");
        }
        boolean taken = profileRepository.existsByNicknameAndIdNotAndDeleted(normalized, userId, YesNo.N);
        return new NicknameCheckResponse(normalized, !taken);
    }

    /**
     * 프로필을 등록하거나 수정한다.
     *
     * @param userId  사용자 ID
     * @param request 프로필 요청
     * @return 저장된 프로필
     * @throws BusinessException 닉네임이 이미 사용 중인 경우
     */
    @Transactional
    public ProfileResponse saveProfile(Long userId, ProfileRequest request) {
        // 닉네임 유니크 제약 위반을 미리 걸러낸다(본인 닉네임 유지는 허용).
        if (profileRepository.existsByNicknameAndIdNotAndDeleted(request.nickname(), userId, YesNo.N)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        // 지역은 검증 없이 저장하면 오타나 임의 값이 들어와 매칭 지역 필터가 조용히
        // 어긋난다. 시/도만 고른 코드도 여기서 걸러진다(선택은 시/군/구까지).
        Region sigungu = regionService.findSelectableSigungu(request.regionCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "지역 코드가 올바르지 않습니다: " + request.regionCode()));

        UserProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> UserProfile.emptyFor(userId));

        profile.setNickname(request.nickname());
        profile.setBirthDate(request.birthDate());
        profile.setGenderCode(emptyToNull(request.genderCode()));
        profile.setRegionCode(sigungu.getCode());
        profile.setMbtiCode(emptyToNull(request.mbtiCode()));
        profile.setOccupation(emptyToNull(request.occupation()));
        profile.setHeightCm(request.heightCm());
        profile.setIntroduction(emptyToNull(request.introduction()));
        // 논리 삭제된 프로필을 다시 등록하는 경우를 대비해 복구해 둔다.
        profile.restore();
        // 상태/완성도 점수는 입력값으로부터 서버가 계산한다.
        profile.refreshCompleteness();

        UserProfile saved = profileRepository.save(profile);

        // 프로필이 있어야 매칭이 가능하므로 선호 조건 기본값도 함께 만들어 둔다.
        if (!preferenceRepository.existsById(userId)) {
            preferenceRepository.save(UserPreference.defaultsFor(userId));
        }

        log.info("프로필 저장: userId={}, status={}, score={}",
                userId, saved.getProfileStatus(), saved.getProfileScore());
        return ProfileResponse.of(saved, sigungu, regionService.findParent(sigungu).orElse(null));
    }

    /**
     * 프로필 엔티티에 지역명을 붙여 응답으로 만든다.
     *
     * @param profile 프로필 엔티티
     * @return 응답 DTO
     */
    private ProfileResponse toProfileResponse(UserProfile profile) {
        Region sigungu = regionService.findSelectableSigungu(profile.getRegionCode()).orElse(null);
        Region sido = sigungu == null ? null : regionService.findParent(sigungu).orElse(null);
        return ProfileResponse.of(profile, sigungu, sido);
    }

    /**
     * 선호 조건을 조회한다. 아직 없으면 기본값을 반환한다.
     *
     * @param userId 사용자 ID
     * @return 선호 조건
     */
    public PreferenceResponse getPreference(Long userId) {
        return preferenceRepository.findByIdAndDeleted(userId, YesNo.N)
                .map(this::toPreferenceResponse)
                .orElseGet(() -> toPreferenceResponse(UserPreference.defaultsFor(userId)));
    }

    /**
     * 선호 조건을 등록하거나 수정한다 (S4 매칭 시작하기).
     *
     * <p>키 조건은 받지 않는다(BMA-19 안건2). 나이 하한 19 는 DTO 검증이 막고,
     * 여기서는 최소·최대 순서와 지역 코드 유효성을 본다.</p>
     *
     * @param userId  사용자 ID
     * @param request 선호 조건 요청
     * @return 저장된 선호 조건
     * @throws BusinessException 최소 나이가 최대 나이보다 크거나 지역 코드가 올바르지 않은 경우
     */
    @Transactional
    public PreferenceResponse savePreference(Long userId, PreferenceRequest request) {
        // DB의 CK_US_PREF_AGE 체크 제약을 애플리케이션에서 먼저 검증한다.
        validateRange(request.minAge(), request.maxAge(), "최소 나이는 최대 나이보다 클 수 없습니다.");

        // S4-04: 시/군/구 하나 또는 "서울 전체"(시/도). 프로필과 달리 시/도도 허용한다.
        // 검증 없이 저장하면 오타나 임의 값이 들어와 매칭 지역 필터가 조용히 어긋난다.
        Region region = regionService.findSelectable(request.preferredRegionCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "희망 지역 코드가 올바르지 않습니다: " + request.preferredRegionCode()));

        UserPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> UserPreference.defaultsFor(userId));

        preference.setPreferredRegionCode(region.getCode());
        preference.setMinAge(request.minAge());
        preference.setMaxAge(request.maxAge());
        preference.setPreferredGenderCode(emptyToNull(request.preferredGenderCode()));
        // null이면 기존 값을 유지한다(부분 수정 허용).
        if (request.maxDistanceKm() != null) {
            preference.setMaxDistanceKm(request.maxDistanceKm());
        }
        if (request.matchingEnabled() != null) {
            preference.setMatchingEnabledYn(YesNo.of(request.matchingEnabled()));
        }
        preference.restore();

        UserPreference saved = preferenceRepository.save(preference);
        log.info("선호 조건 저장: userId={}, region={}({}), age={}~{}",
                userId, region.getCode(), region.isSido() ? "시/도 전체" : "시/군/구",
                saved.getMinAge(), saved.getMaxAge());
        return PreferenceResponse.of(saved, region, regionService.findSidoOf(region).orElse(null));
    }

    /**
     * 선호 조건 엔티티에 지역명을 붙여 응답으로 만든다.
     *
     * @param preference 선호 조건 엔티티
     * @return 응답 DTO
     */
    private PreferenceResponse toPreferenceResponse(UserPreference preference) {
        Region region = regionService.findSelectable(preference.getPreferredRegionCode()).orElse(null);
        Region sido = region == null ? null : regionService.findSidoOf(region).orElse(null);
        return PreferenceResponse.of(preference, region, sido);
    }

    /**
     * 내 프로필 이미지를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 등록된 이미지. 아직 올리지 않았으면 비어 있음
     */
    public Optional<ProfileImageResponse> getImage(Long userId) {
        return currentImage(userId).map(ProfileImageResponse::from);
    }

    /**
     * 프로필 이미지를 등록한다. 이미 있으면 교체한다.
     *
     * <p>사진은 1장뿐이라 "추가"가 아니라 "교체"다. 저장소에 새 파일을 먼저 올린
     * 뒤에 기존 것을 지우므로, 업로드가 검증에서 실패하면 기존 사진이 그대로 남는다.</p>
     *
     * @param userId 사용자 ID
     * @param file   업로드 파일
     * @return 저장된 이미지 정보
     * @throws BusinessException 파일 검증 실패
     */
    @Transactional
    public ProfileImageResponse saveImage(Long userId, MultipartFile file) {
        // 파일 형식/크기/시그니처 검증은 저장소 구현이 담당한다.
        StorageService.StoredFile stored = storageService.upload(userId, file);

        // 다중 업로드를 허용하던 시절에 쌓인 사진이 있을 수 있어 전부 정리한다.
        removeAllImages(userId);

        ProfileImage image = new ProfileImage();
        image.setUserId(userId);
        image.setStorageType("LOCAL");
        image.setOriginalObjectKey(stored.objectKey());
        image.setOriginalFileName(stored.originalName());
        image.setContentType(stored.contentType());
        image.setFileSize(stored.size());
        image.setFileHash(stored.sha256());
        // 1장뿐이므로 순서와 대표 여부는 언제나 같은 값이다. 컬럼은 다중 이미지
        // 전제로 남아 있어 채워 두기만 한다(구조 단순화는 BMA-14 확인 후).
        image.setDisplayOrder(1);
        image.markPrimary(true);

        log.info("프로필 사진 등록: userId={}, size={}", userId, stored.size());
        return ProfileImageResponse.from(imageRepository.save(image));
    }

    /**
     * 프로필 이미지를 삭제한다. 기본 아바타로 돌아간다.
     *
     * @param userId 사용자 ID
     * @throws BusinessException 등록된 사진이 없는 경우
     */
    @Transactional
    public void deleteImage(Long userId) {
        if (currentImage(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "등록된 프로필 사진이 없습니다.");
        }
        removeAllImages(userId);
        log.info("프로필 사진 삭제: userId={}", userId);
    }

    /**
     * 현재 사진 한 건을 찾는다.
     *
     * @param userId 사용자 ID
     * @return 사진. 없으면 비어 있음
     */
    private Optional<ProfileImage> currentImage(Long userId) {
        return imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(userId, YesNo.N).stream()
                .findFirst();
    }

    /**
     * 사용자의 사진을 DB와 저장소에서 모두 지운다.
     *
     * <p>DB 플래그만 바꾸면 저장소에 고아 파일이 계속 쌓이므로 실제 파일도 함께 지운다.</p>
     *
     * @param userId 사용자 ID
     */
    private void removeAllImages(Long userId) {
        for (ProfileImage image : imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(userId, YesNo.N)) {
            image.markDeleted();
            storageService.delete(image.getOriginalObjectKey());
        }
    }

    /**
     * 최소/최대 범위의 정합성을 확인한다.
     *
     * @param min     최소값
     * @param max     최대값
     * @param message 위반 시 메시지
     */
    private void validateRange(Integer min, Integer max, String message) {
        if (min != null && max != null && min > max) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    /**
     * 빈 문자열을 null로 정규화한다. 선택 항목에 빈 문자열이 저장되는 것을 막는다.
     *
     * @param value 원본 값
     * @return 공백만 있으면 {@code null}, 아니면 앞뒤 공백을 제거한 값
     */
    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
