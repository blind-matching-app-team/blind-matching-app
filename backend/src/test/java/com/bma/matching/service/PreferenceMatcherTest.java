package com.bma.matching.service;

import com.bma.user.entity.UserPreference;
import com.bma.user.entity.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기열 매칭의 선호 조건 판정이 추천 목록 필터와 같은 규칙인지 고정한다(성별·나이·지역, 키 없음).
 */
class PreferenceMatcherTest {

    private static final Function<String, String> PARENT = code ->
            Map.of("SEOUL_GANGNAM", "SEOUL", "SEOUL_JUNG", "SEOUL", "BUSAN_HAEUNDAE", "BUSAN").get(code);

    private static UserProfile profile(String gender, int age, String region) {
        UserProfile profile = UserProfile.emptyFor(1L);
        profile.setGenderCode(gender);
        profile.setBirthDate(LocalDate.now().minusYears(age).minusDays(1));
        profile.setRegionCode(region);
        return profile;
    }

    private static UserPreference preference(String gender, Integer minAge, Integer maxAge, String region) {
        UserPreference preference = new UserPreference();
        preference.setPreferredGenderCode(gender);
        preference.setMinAge(minAge);
        preference.setMaxAge(maxAge);
        preference.setPreferredRegionCode(region);
        return preference;
    }

    @Test
    @DisplayName("선호 조건이 없으면 누구든 받아들인다")
    void noPreferenceAcceptsAll() {
        assertThat(PreferenceMatcher.accepts(null, profile("F", 30, "SEOUL_JUNG"), PARENT)).isTrue();
        assertThat(PreferenceMatcher.accepts(preference(null, null, null, null),
                profile("M", 50, null), PARENT)).isTrue();
    }

    @Test
    @DisplayName("성별과 나이 범위(만 나이, 경계 포함)를 판정한다")
    void genderAndAge() {
        UserPreference wantsF25to30 = preference("F", 25, 30, null);
        assertThat(PreferenceMatcher.accepts(wantsF25to30, profile("F", 25, null), PARENT)).isTrue();
        assertThat(PreferenceMatcher.accepts(wantsF25to30, profile("F", 30, null), PARENT)).isTrue();
        assertThat(PreferenceMatcher.accepts(wantsF25to30, profile("F", 24, null), PARENT)).isFalse();
        assertThat(PreferenceMatcher.accepts(wantsF25to30, profile("F", 31, null), PARENT)).isFalse();
        assertThat(PreferenceMatcher.accepts(wantsF25to30, profile("M", 27, null), PARENT)).isFalse();
    }

    @Test
    @DisplayName("희망 지역은 시/군/구 일치 또는 그 시/도 하위면 통과, 다른 시/도는 거부")
    void region() {
        UserPreference wantsSeoul = preference(null, null, null, "SEOUL");
        assertThat(PreferenceMatcher.accepts(wantsSeoul, profile("F", 30, "SEOUL_GANGNAM"), PARENT)).isTrue();
        assertThat(PreferenceMatcher.accepts(wantsSeoul, profile("F", 30, "SEOUL"), PARENT)).isTrue();
        assertThat(PreferenceMatcher.accepts(wantsSeoul, profile("F", 30, "BUSAN_HAEUNDAE"), PARENT)).isFalse();
        assertThat(PreferenceMatcher.accepts(wantsSeoul, profile("F", 30, null), PARENT)).isFalse();

        UserPreference wantsGangnam = preference(null, null, null, "SEOUL_GANGNAM");
        assertThat(PreferenceMatcher.accepts(wantsGangnam, profile("F", 30, "SEOUL_GANGNAM"), PARENT)).isTrue();
        assertThat(PreferenceMatcher.accepts(wantsGangnam, profile("F", 30, "SEOUL_JUNG"), PARENT)).isFalse();
    }
}
