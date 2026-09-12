package com.bma.user.dto;

import com.bma.user.dto.UserDtos.PreferenceRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S4 선호조건 요청의 입력 규칙을 고정한다.
 *
 * <p>S4-09 [결정 v1.5]: 나이는 19~99 이고 최소값 19 는 고정이다. 앱이 성인 전용
 * 정책(BMA-19 안건3)이라 매칭 조건에서 미성년자 범위를 설정할 수 없어야 한다.
 * 이 경계가 풀리면 "18세 이상" 같은 조건이 저장된다.</p>
 *
 * <p>S4-05 / BMA-19 안건2: 키는 필터가 아니다. 요청에 키 필드가 없다는 것 자체를
 * 테스트로 고정해 나중에 누가 다시 넣는 것을 막는다.</p>
 */
class PreferenceRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<PreferenceRequest>> validate(String region, Integer minAge, Integer maxAge) {
        return validator.validate(new PreferenceRequest(region, minAge, maxAge, null, null, null));
    }

    @Test
    @DisplayName("지역 + 나이 19~99 는 통과한다")
    void validRequest() {
        assertThat(validate("SEOUL_GANGNAM", 19, 99)).isEmpty();
    }

    @Test
    @DisplayName("나이를 비우면 '제한 없음'이라 통과한다 (S4-09 기본값)")
    void ageIsOptional() {
        assertThat(validate("SEOUL", null, null)).isEmpty();
    }

    @Test
    @DisplayName("최소 나이 18 은 거부한다 — 미성년자 범위를 설정할 수 없다")
    void rejectsMinorMinAge() {
        assertThat(validate("SEOUL", 18, 30))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("minAge");
    }

    @Test
    @DisplayName("최대 나이 100 은 거부한다")
    void rejectsMaxAgeOver99() {
        assertThat(validate("SEOUL", 20, 100))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("maxAge");
    }

    @Test
    @DisplayName("희망 지역은 필수다 — S4 에서 유일한 필수 조건")
    void regionIsRequired() {
        assertThat(validate("  ", 19, 99))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("preferredRegionCode");
    }

    @Test
    @DisplayName("요청에 키 필드가 없다 — BMA-19 안건2, 다시 넣지 말 것")
    void noHeightField() {
        assertThat(PreferenceRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase().contains("height"));
    }
}
