package com.bma.user.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.storage.StorageService;
import com.bma.user.dto.UserDtos.ImageOrder;
import com.bma.user.dto.UserDtos.ImageOrderRequest;
import com.bma.user.dto.UserDtos.MeResponse;
import com.bma.user.dto.UserDtos.PreferenceRequest;
import com.bma.user.dto.UserDtos.PreferenceResponse;
import com.bma.user.dto.UserDtos.ProfileImageResponse;
import com.bma.user.dto.UserDtos.ProfileRequest;
import com.bma.user.dto.UserDtos.ProfileResponse;
import com.bma.common.config.AppProperties;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 회원 프로필 / 선호 조건 / 프로필 이미지 관리.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li>엔티티 대신 DTO를 반환해 비밀번호 해시 등 내부 필드 노출을 차단했다.</li>
 *   <li>닉네임 중복을 사전 확인해 유니크 제약 위반으로 인한 500을 없앴다.</li>
 *   <li>이미지 순서 변경 시 대표 이미지가 여러 개가 되지 않도록 한 건만 남긴다.</li>
 *   <li>사용자당 이미지 개수 한도를 적용했다.</li>
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
    private final StorageService storageService;
    private final AppProperties properties;

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
                .map(ProfileResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
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

        UserProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> UserProfile.emptyFor(userId));

        profile.setNickname(request.nickname());
        profile.setBirthDate(request.birthDate());
        profile.setGenderCode(request.genderCode());
        profile.setRegionCode(request.regionCode());
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
        return ProfileResponse.from(saved);
    }

    /**
     * 선호 조건을 조회한다. 아직 없으면 기본값을 반환한다.
     *
     * @param userId 사용자 ID
     * @return 선호 조건
     */
    public PreferenceResponse getPreference(Long userId) {
        return preferenceRepository.findByIdAndDeleted(userId, YesNo.N)
                .map(PreferenceResponse::from)
                .orElseGet(() -> PreferenceResponse.from(UserPreference.defaultsFor(userId)));
    }

    /**
     * 선호 조건을 등록하거나 수정한다.
     *
     * @param userId  사용자 ID
     * @param request 선호 조건 요청
     * @return 저장된 선호 조건
     * @throws BusinessException 최소값이 최대값보다 큰 경우
     */
    @Transactional
    public PreferenceResponse savePreference(Long userId, PreferenceRequest request) {
        // DB의 CK_US_PREF_AGE / CK_US_PREF_HEIGHT 체크 제약을 애플리케이션에서 먼저 검증한다.
        validateRange(request.minAge(), request.maxAge(), "최소 연령은 최대 연령보다 클 수 없습니다.");
        validateRange(request.minHeightCm(), request.maxHeightCm(), "최소 키는 최대 키보다 클 수 없습니다.");

        UserPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> UserPreference.defaultsFor(userId));

        preference.setPreferredGenderCode(emptyToNull(request.preferredGenderCode()));
        preference.setMinAge(request.minAge());
        preference.setMaxAge(request.maxAge());
        preference.setMinHeightCm(request.minHeightCm());
        preference.setMaxHeightCm(request.maxHeightCm());
        preference.setPreferredRegionCode(emptyToNull(request.preferredRegionCode()));
        // null이면 기존 값을 유지한다(부분 수정 허용).
        if (request.maxDistanceKm() != null) {
            preference.setMaxDistanceKm(request.maxDistanceKm());
        }
        if (request.matchingEnabled() != null) {
            preference.setMatchingEnabledYn(YesNo.of(request.matchingEnabled()));
        }
        preference.restore();

        return PreferenceResponse.from(preferenceRepository.save(preference));
    }

    /**
     * 내 프로필 이미지 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 표시 순서대로 정렬된 이미지 목록
     */
    public List<ProfileImageResponse> getImages(Long userId) {
        return imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(userId, YesNo.N).stream()
                .map(ProfileImageResponse::from)
                .toList();
    }

    /**
     * 프로필 이미지를 업로드한다.
     *
     * @param userId 사용자 ID
     * @param file   업로드 파일
     * @return 저장된 이미지 정보
     * @throws BusinessException 보유 한도 초과 또는 파일 검증 실패
     */
    @Transactional
    public ProfileImageResponse uploadImage(Long userId, MultipartFile file) {
        long count = imageRepository.countByUserIdAndDeleted(userId, YesNo.N);
        int limit = properties.storage().maxImagesPerUser();
        if (count >= limit) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED,
                    "이미지는 최대 " + limit + "장까지 등록할 수 있습니다.");
        }

        // 파일 형식/크기/시그니처 검증은 저장소 구현이 담당한다.
        StorageService.StoredFile stored = storageService.upload(userId, file);

        ProfileImage image = new ProfileImage();
        image.setUserId(userId);
        image.setStorageType("LOCAL");
        image.setOriginalObjectKey(stored.objectKey());
        image.setOriginalFileName(stored.originalName());
        image.setContentType(stored.contentType());
        image.setFileSize(stored.size());
        image.setFileHash(stored.sha256());
        image.setDisplayOrder((int) count + 1);
        // 첫 번째 이미지는 자동으로 대표 이미지가 된다.
        image.markPrimary(count == 0);

        return ProfileImageResponse.from(imageRepository.save(image));
    }

    /**
     * 프로필 이미지를 삭제한다.
     *
     * @param userId  사용자 ID
     * @param imageId 이미지 ID
     * @throws BusinessException 본인 소유가 아니거나 존재하지 않는 경우
     */
    @Transactional
    public void deleteImage(Long userId, Long imageId) {
        // 소유자 조건을 쿼리에 포함해 타인 이미지의 존재 여부조차 드러나지 않게 한다.
        ProfileImage image = imageRepository.findByIdAndUserIdAndDeleted(imageId, userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        image.markDeleted();
        // DB 플래그만 바꾸면 저장소에 고아 파일이 계속 쌓이므로 실제 파일도 지운다.
        storageService.delete(image.getOriginalObjectKey());

        // 대표 이미지를 지웠다면 남은 이미지 중 첫 번째를 대표로 승격한다.
        if (image.isPrimary()) {
            imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(userId, YesNo.N).stream()
                    .filter(remaining -> !remaining.getId().equals(imageId))
                    .findFirst()
                    .ifPresent(remaining -> remaining.markPrimary(true));
        }
    }

    /**
     * 프로필 이미지 표시 순서와 대표 이미지를 일괄 변경한다.
     *
     * @param userId  사용자 ID
     * @param request 변경 요청
     * @throws BusinessException 본인 소유가 아닌 이미지가 포함된 경우
     */
    @Transactional
    public void reorderImages(Long userId, ImageOrderRequest request) {
        // 요청에 포함된 ID를 한 번에 조회해 N+1 쿼리를 피한다.
        Map<Long, ProfileImage> ownedImages =
                imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(userId, YesNo.N).stream()
                        .collect(Collectors.toMap(ProfileImage::getId, Function.identity()));

        // 대표 이미지는 한 장뿐이어야 하므로, 요청에서 가장 먼저 지정된 것만 인정한다.
        Long newPrimaryId = request.images().stream()
                .filter(order -> Boolean.TRUE.equals(order.primary()))
                .map(ImageOrder::imageId)
                .findFirst()
                .orElse(null);

        for (ImageOrder order : request.images()) {
            ProfileImage image = ownedImages.get(order.imageId());
            if (image == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "본인의 이미지가 아니거나 존재하지 않습니다: imageId=" + order.imageId());
            }
            image.setDisplayOrder(order.displayOrder());
        }

        if (newPrimaryId != null) {
            ownedImages.values().forEach(image -> image.markPrimary(image.getId().equals(newPrimaryId)));
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
