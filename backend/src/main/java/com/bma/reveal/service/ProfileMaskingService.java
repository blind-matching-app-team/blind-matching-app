package com.bma.reveal.service;

import com.bma.common.entity.YesNo;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.bma.reveal.entity.RevealPolicy;
import com.bma.user.entity.ProfileImage;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.ProfileImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 공개 단계에 맞춰 상대 프로필을 마스킹하는 서비스.
 *
 * <p>이 서비스가 필요한 이유: 기존 추천 API는 {@code UserProfile} 엔티티를 그대로 반환해
 * 매칭이 성사되기도 전에 닉네임·생년월일·직업·정확한 키가 모두 노출됐다.
 * "블라인드" 소개팅이라는 제품 컨셉이 사실상 무력화된 상태였다.
 * 상대 정보를 클라이언트로 내보내는 모든 경로는 반드시 이 서비스를 거친다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileMaskingService {

    private final ProfileImageRepository imageRepository;

    /**
     * 프로필 1건을 지정한 공개 단계로 마스킹한다.
     *
     * @param profile 원본 프로필
     * @param level   적용할 공개 단계
     * @return 마스킹된 프로필
     */
    public MaskedProfileResponse mask(UserProfile profile, int level) {
        List<ProfileImage> images =
                imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(profile.getId(), YesNo.N);
        return mask(profile, images, level);
    }

    /**
     * 여러 프로필을 한 번에 마스킹한다.
     *
     * <p>추천 목록처럼 다건을 처리할 때 프로필마다 이미지 쿼리를 날리면 N+1이 되므로,
     * 이미지 조회 결과를 미리 넘겨받아 메모리에서 묶는다.</p>
     *
     * @param profiles      원본 프로필 목록
     * @param imagesByOwner 사용자 ID → 이미지 목록
     * @param level         적용할 공개 단계
     * @return 마스킹된 프로필 목록
     */
    public List<MaskedProfileResponse> maskAll(List<UserProfile> profiles,
                                               Map<Long, List<ProfileImage>> imagesByOwner,
                                               int level) {
        return profiles.stream()
                .map(profile -> mask(profile, imagesByOwner.getOrDefault(profile.getId(), List.of()), level))
                .toList();
    }

    /**
     * 프로필과 이미지 목록을 지정한 단계로 마스킹한다.
     *
     * @param profile 원본 프로필
     * @param images  해당 사용자의 이미지 목록
     * @param level   적용할 공개 단계
     * @return 마스킹된 프로필
     */
    public MaskedProfileResponse mask(UserProfile profile, List<ProfileImage> images, int level) {
        boolean partialOrAbove = level >= RevealPolicy.LEVEL_PARTIAL;
        boolean fullReveal = level >= RevealPolicy.LEVEL_FULL;

        Integer age = profile.age();

        return new MaskedProfileResponse(
                profile.getId(),
                level,
                // 닉네임은 개인을 특정할 수 있는 단서라 전체 공개 단계에서만 내려준다.
                fullReveal ? profile.getNickname() : null,
                partialOrAbove ? age : null,
                toAgeGroup(age),
                profile.getGenderCode(),
                // 지역은 시/도 수준까지만 노출한다(코드 앞 2자리 규칙).
                maskRegion(profile.getRegionCode(), fullReveal),
                profile.getMbtiCode(),
                partialOrAbove ? profile.getOccupation() : null,
                partialOrAbove ? profile.getHeightCm() : null,
                profile.getIntroduction(),
                selectImageKeys(images, level));
    }

    /**
     * 단계에 맞는 이미지 키만 고른다.
     *
     * <p>레벨별로 다른 컬럼을 사용하므로, 원본 키가 실수로 낮은 단계에 섞여 나갈 수 없다.
     * 해당 단계용 가공 이미지가 아직 없으면 그 이미지는 목록에서 제외한다.</p>
     *
     * @param images 이미지 목록
     * @param level  공개 단계
     * @return 오브젝트 키 목록
     */
    private List<String> selectImageKeys(List<ProfileImage> images, int level) {
        return images.stream()
                .map(image -> switch (Math.min(Math.max(level, RevealPolicy.LEVEL_HIDDEN), RevealPolicy.LEVEL_FULL)) {
                    case RevealPolicy.LEVEL_FULL -> image.getOriginalObjectKey();
                    case RevealPolicy.LEVEL_PARTIAL -> image.getBlurredObjectKey();
                    default -> image.getSilhouetteObjectKey();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 지역 코드를 시/도 수준으로 축약한다.
     *
     * @param regionCode 원본 지역 코드
     * @param fullReveal 전체 공개 여부
     * @return 노출할 지역 코드
     */
    private String maskRegion(String regionCode, boolean fullReveal) {
        if (regionCode == null || fullReveal) {
            return regionCode;
        }
        // 예) "SEOUL_GANGNAM" → "SEOUL"
        int separator = regionCode.indexOf('_');
        return separator > 0 ? regionCode.substring(0, separator) : regionCode;
    }

    /**
     * 정확한 나이 대신 사용할 나이대 표기를 만든다.
     *
     * @param age 만 나이
     * @return "20대 초반" 형태의 문자열. 나이를 알 수 없으면 {@code null}
     */
    private String toAgeGroup(Integer age) {
        if (age == null) {
            return null;
        }
        int decade = age / 10 * 10;
        int remainder = age % 10;
        String phase = remainder < 4 ? "초반" : (remainder < 7 ? "중반" : "후반");
        return decade + "대 " + phase;
    }

    /**
     * 사용자별 이미지 목록을 한 번에 조회한다.
     *
     * @param userIds 사용자 ID 목록
     * @return 사용자 ID → 이미지 목록
     */
    public Map<Long, List<ProfileImage>> loadImagesByOwner(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return imageRepository.findByUserIdInAndDeletedOrderByDisplayOrderAsc(userIds, YesNo.N).stream()
                .collect(Collectors.groupingBy(ProfileImage::getUserId));
    }
}
