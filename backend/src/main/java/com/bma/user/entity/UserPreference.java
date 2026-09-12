package com.bma.user.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 매칭 상대 선호 조건({@code US_USER_PREFERENCE}).
 *
 * <p>추천 쿼리의 필터 조건으로 사용된다. 기존 구현은 이 값을 저장만 하고
 * 추천에 전혀 반영하지 않아 선호 조건이 무의미했다.</p>
 */
@Entity
@Table(name = "US_USER_PREFERENCE")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference extends BaseAuditEntity {

    /** 사용자 ID(PK이자 {@code US_USER}에 대한 FK). */
    @Id
    @Column(name = "USER_ID")
    private Long id;

    /** 선호 성별 코드. null이면 성별을 가리지 않는다. */
    @Column(name = "PREFERRED_GENDER_CODE")
    private String preferredGenderCode;

    // 아래 숫자 컬럼들은 스키마가 UNSIGNED 타입이라 Integer 기본 매핑과 어긋난다.
    // ddl-auto=validate 를 통과하도록 columnDefinition 을 명시한다
    // (BaseAuditEntity.deleted 주석 참고).

    /** 최소 희망 연령(만 나이). */
    @Column(name = "MIN_AGE", columnDefinition = "SMALLINT UNSIGNED")
    private Integer minAge;

    /** 최대 희망 연령(만 나이). */
    @Column(name = "MAX_AGE", columnDefinition = "SMALLINT UNSIGNED")
    private Integer maxAge;

    // 희망 키(MIN/MAX_HEIGHT_CM)는 V8 에서 지웠다. BMA-19 안건2: 키는 "외모보다 대화"
    // 컨셉과 충돌하는 요소라 매칭 필터에서 영구 제외한다(기술 미확정이 아니라 의도된 설계).

    /** 최대 거리(km). 위치 기반 매칭 도입 전까지는 저장만 한다. */
    @Column(name = "MAX_DISTANCE_KM", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer maxDistanceKm = 50;

    /**
     * 희망 지역 코드({@code CM_REGION}).
     *
     * <p>S4-04 는 드롭다운 하나로 시/군/구를 고르거나 "서울 전체"처럼 시/도를 고른다.
     * 시/도 코드가 들어 있으면 그 하위 시/군/구 전체를 뜻한다.</p>
     */
    @Column(name = "PREFERRED_REGION_CODE", length = 30)
    private String preferredRegionCode;

    /** 추천/매칭 참여 여부. {@code 'N'}이면 추천 대상에서 제외된다. */
    @Column(name = "MATCHING_ENABLED_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String matchingEnabledYn = YesNo.Y;

    /**
     * 기본값으로 채워진 선호 조건을 만든다.
     *
     * @param userId 사용자 ID
     * @return 선호 조건 엔티티
     */
    public static UserPreference defaultsFor(Long userId) {
        UserPreference preference = new UserPreference();
        preference.id = userId;
        return preference;
    }

    /**
     * 매칭 참여 의사가 있는지 확인한다.
     *
     * @return 참여 상태이면 {@code true}
     */
    public boolean isMatchingEnabled() {
        return YesNo.isY(matchingEnabledYn);
    }
}
