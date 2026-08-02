package com.bma.reveal.service;

import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.bma.reveal.entity.RevealPolicy;
import com.bma.user.entity.ProfileImage;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.ProfileImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProfileMaskingService} 단위 테스트.
 *
 * <p>블라인드 서비스의 핵심 규칙을 회귀 테스트로 고정한다.
 * 기존 추천 API는 프로필 엔티티를 그대로 반환해 매칭 전부터 닉네임·직업·정확한 나이가
 * 모두 노출되고 있었다.</p>
 */
class ProfileMaskingServiceTest {

    private ProfileMaskingService maskingService;

    @BeforeEach
    void setUp() {
        // 이미지 조회는 이 테스트의 관심사가 아니므로 목으로 대체한다.
        maskingService = new ProfileMaskingService(Mockito.mock(ProfileImageRepository.class));
    }

    @Test
    @DisplayName("레벨 0에서는 닉네임·직업·정확한 나이·키가 노출되지 않는다")
    void mask_level0_hidesIdentifyingFields() {
        MaskedProfileResponse masked =
                maskingService.mask(sampleProfile(), sampleImages(), RevealPolicy.LEVEL_HIDDEN);

        assertThat(masked.nickname()).isNull();
        assertThat(masked.age()).isNull();
        assertThat(masked.occupation()).isNull();
        assertThat(masked.heightCm()).isNull();

        // 나이대와 자기소개는 상대를 특정하지 않으므로 노출한다.
        assertThat(masked.ageGroup()).isNotNull();
        assertThat(masked.introduction()).isEqualTo("안녕하세요");
        // 지역은 시/도 수준까지만.
        assertThat(masked.regionCode()).isEqualTo("SEOUL");
        // 이미지는 실루엣만.
        assertThat(masked.imageKeys()).containsExactly("silhouette/1.png");
    }

    @Test
    @DisplayName("레벨 1에서는 나이·키·직업이 공개되지만 닉네임은 여전히 감춘다")
    void mask_level1_revealsPartialFields() {
        MaskedProfileResponse masked =
                maskingService.mask(sampleProfile(), sampleImages(), RevealPolicy.LEVEL_PARTIAL);

        assertThat(masked.nickname()).isNull();
        assertThat(masked.age()).isNotNull();
        assertThat(masked.occupation()).isEqualTo("디자이너");
        assertThat(masked.heightCm()).isEqualTo(172);
        assertThat(masked.imageKeys()).containsExactly("blurred/1.png");
    }

    @Test
    @DisplayName("레벨 2에서는 닉네임과 원본 이미지까지 모두 공개된다")
    void mask_level2_revealsEverything() {
        MaskedProfileResponse masked =
                maskingService.mask(sampleProfile(), sampleImages(), RevealPolicy.LEVEL_FULL);

        assertThat(masked.nickname()).isEqualTo("바다");
        assertThat(masked.age()).isNotNull();
        assertThat(masked.regionCode()).isEqualTo("SEOUL_GANGNAM");
        assertThat(masked.imageKeys()).containsExactly("original/1.png");
    }

    @Test
    @DisplayName("해당 단계용 이미지가 없으면 그 이미지는 목록에서 제외된다")
    void mask_skipsMissingImageVariant() {
        ProfileImage onlyOriginal = new ProfileImage();
        onlyOriginal.setUserId(1L);
        onlyOriginal.setOriginalObjectKey("original/2.png");
        // 블러/실루엣이 아직 생성되지 않은 상태

        MaskedProfileResponse masked = maskingService.mask(
                sampleProfile(), List.of(onlyOriginal), RevealPolicy.LEVEL_HIDDEN);

        // 원본 키가 낮은 단계에 새어 나가면 안 된다.
        assertThat(masked.imageKeys()).isEmpty();
    }

    /**
     * 테스트용 프로필을 만든다.
     *
     * @return 프로필 엔티티
     */
    private UserProfile sampleProfile() {
        UserProfile profile = UserProfile.emptyFor(1L);
        profile.setNickname("바다");
        profile.setBirthDate(LocalDate.now().minusYears(28));
        profile.setGenderCode("FEMALE");
        profile.setRegionCode("SEOUL_GANGNAM");
        profile.setMbtiCode("INFJ");
        profile.setOccupation("디자이너");
        profile.setHeightCm(172);
        profile.setIntroduction("안녕하세요");
        return profile;
    }

    /**
     * 세 단계 이미지가 모두 준비된 테스트용 이미지를 만든다.
     *
     * @return 이미지 목록
     */
    private List<ProfileImage> sampleImages() {
        ProfileImage image = new ProfileImage();
        image.setUserId(1L);
        image.setOriginalObjectKey("original/1.png");
        image.setBlurredObjectKey("blurred/1.png");
        image.setSilhouetteObjectKey("silhouette/1.png");
        return List.of(image);
    }
}
