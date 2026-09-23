package com.bma.matching.service;

import com.bma.user.entity.UserPreference;
import com.bma.user.entity.UserProfile;

import java.util.function.Function;

/**
 * 선호 조건이 상대 프로필을 받아들이는지 판정한다 (대기열 매칭용, 자바 버전).
 *
 * <p>추천 목록의 QueryDSL 필터({@code MatchingQueryRepository.applyPreferenceFilters})와 같은 규칙이다:
 * 성별 일치, 만 나이 범위, 희망 지역(시/군/구 일치 또는 그 시/도 하위). 키 조건은 없다(BMA-19 안건2).
 * 선호 조건이 없으면 모두 받아들인다.</p>
 */
public final class PreferenceMatcher {

    private PreferenceMatcher() {
    }

    /**
     * 판정한다.
     *
     * @param preference 판정 주체의 선호 조건({@code null}이면 조건 없음)
     * @param candidate  상대 프로필
     * @param parentOf   지역 코드 → 상위(시/도) 코드. 없으면 {@code null}
     * @return 받아들이면 {@code true}
     */
    public static boolean accepts(UserPreference preference, UserProfile candidate,
                                  Function<String, String> parentOf) {
        if (preference == null) {
            return true;
        }
        if (hasText(preference.getPreferredGenderCode())
                && !preference.getPreferredGenderCode().equals(candidate.getGenderCode())) {
            return false;
        }
        Integer age = candidate.age();
        if (preference.getMinAge() != null && (age == null || age < preference.getMinAge())) {
            return false;
        }
        if (preference.getMaxAge() != null && (age == null || age > preference.getMaxAge())) {
            return false;
        }
        if (hasText(preference.getPreferredRegionCode())) {
            String region = candidate.getRegionCode();
            if (region == null) {
                return false;
            }
            String wanted = preference.getPreferredRegionCode();
            if (!wanted.equals(region) && !wanted.equals(parentOf.apply(region))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
