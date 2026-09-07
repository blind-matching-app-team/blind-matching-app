package com.bma.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필 완성 판정과 매칭 가능 판정이 서로 다른 조건이라는 것을 고정한다.
 *
 * <p>S3 화면에 성별 입력란이 없어 완성 판정에서 성별을 뺐다. 이 구분이 깨지면
 * 사용자가 화면을 다 채워도 {@code profileCompleted=false}가 되어 프론트가
 * S3로 되돌리는 무한 루프에 빠지거나, 반대로 성별 없는 사용자가 매칭에 들어간다.</p>
 */
class UserProfileCompletenessTest {

    private UserProfile profileWith(String nickname, LocalDate birthDate, String regionCode, String genderCode) {
        UserProfile profile = UserProfile.emptyFor(1L);
        profile.setNickname(nickname);
        profile.setBirthDate(birthDate);
        profile.setRegionCode(regionCode);
        profile.setGenderCode(genderCode);
        profile.refreshCompleteness();
        return profile;
    }

    @Test
    @DisplayName("닉네임·생년월일·지역만 채우면 성별이 없어도 프로필은 완성이다")
    void completeWithoutGender() {
        UserProfile profile = profileWith("블라인드", LocalDate.of(1998, 3, 14), "SEOUL_GANGNAM", null);

        assertThat(profile.getProfileStatus()).isEqualTo(UserProfile.STATUS_COMPLETE);
        assertThat(profile.isComplete()).isTrue();
    }

    @Test
    @DisplayName("성별이 없으면 프로필이 완성이어도 매칭에는 넣지 않는다")
    void notMatchableWithoutGender() {
        UserProfile profile = profileWith("블라인드", LocalDate.of(1998, 3, 14), "SEOUL_GANGNAM", null);

        assertThat(profile.isComplete()).isTrue();
        assertThat(profile.isMatchable()).isFalse();
    }

    @Test
    @DisplayName("성별까지 있으면 매칭 가능하다")
    void matchableWithGender() {
        UserProfile profile = profileWith("블라인드", LocalDate.of(1998, 3, 14), "SEOUL_GANGNAM", "MALE");

        assertThat(profile.isMatchable()).isTrue();
    }

    @Test
    @DisplayName("지역이 비어 있으면 미완성이고 점수는 0이다")
    void incompleteWithoutRegion() {
        UserProfile profile = profileWith("블라인드", LocalDate.of(1998, 3, 14), null, "MALE");

        assertThat(profile.getProfileStatus()).isEqualTo(UserProfile.STATUS_INCOMPLETE);
        assertThat(profile.isComplete()).isFalse();
        // 필수를 못 채우면 기본 점수가 붙지 않는다. 선택 항목(성별) 점수만 남는다.
        assertThat(profile.getProfileScore()).isEqualTo(10);
    }

    @Test
    @DisplayName("선택 항목을 모두 채우면 점수가 정확히 100이 된다")
    void scoreCapsAtHundred() {
        UserProfile profile = UserProfile.emptyFor(1L);
        profile.setNickname("블라인드");
        profile.setBirthDate(LocalDate.of(1998, 3, 14));
        profile.setRegionCode("SEOUL_GANGNAM");
        profile.setGenderCode("MALE");
        profile.setMbtiCode("INFP");
        profile.setOccupation("개발자");
        profile.setHeightCm(175);
        profile.setIntroduction("스무 자를 넘기기 위한 충분히 긴 자기소개 문장입니다.");
        profile.refreshCompleteness();

        // 성별이 선택 항목으로 옮겨가면서 배점을 다시 나눴다. 합이 100을 넘으면 배점이 어긋난 것이다.
        assertThat(profile.getProfileScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("논리 삭제된 프로필은 완성 상태여도 매칭에 들어가지 않는다")
    void deletedProfileIsNotMatchable() {
        UserProfile profile = profileWith("블라인드", LocalDate.of(1998, 3, 14), "SEOUL_GANGNAM", "MALE");
        profile.markDeleted();

        assertThat(profile.isComplete()).isFalse();
        assertThat(profile.isMatchable()).isFalse();
    }
}
