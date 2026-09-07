package com.bma.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 행정구역({@code CM_REGION}). 시/도 → 시/군/구 2단계다.
 *
 * <p>{@link com.bma.common.entity.YesNo} 공통 코드 테이블({@code CM_CODE})에 넣지 않은 이유는
 * 상위-하위 관계를 표현할 컬럼이 없어서다. 행정구역은 개편이 잦아 자체 수명주기를 갖는다.</p>
 */
@Entity
@Table(name = "CM_REGION")
@Getter
@Setter
@NoArgsConstructor
public class Region extends BaseAuditEntity {

    /** 시/도. */
    public static final int LEVEL_SIDO = 1;

    /** 시/군/구. */
    public static final int LEVEL_SIGUNGU = 2;

    /** 지역 코드(PK). 내부 식별자이며 행정안전부 표준 코드가 아니다. */
    @Id
    @Column(name = "REGION_CODE", length = 20)
    private String code;

    /** 상위 지역 코드. 시/도 자신은 {@code null}이다. */
    @Column(name = "PARENT_REGION_CODE", length = 20)
    private String parentCode;

    /** 지역명. */
    @Column(name = "REGION_NAME", nullable = false, length = 100)
    private String name;

    /** 단계. {@link #LEVEL_SIDO} 또는 {@link #LEVEL_SIGUNGU}. */
    @Column(name = "REGION_LEVEL", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer level;

    /** 행정안전부 표준 행정구역 코드. 데이터 소스 확정 전이라 아직 비어 있다. */
    @Column(name = "LEGAL_CODE", length = 10)
    private String legalCode;

    /** 같은 상위 안에서의 정렬 순서. */
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    /** 사용 여부. */
    @Column(name = "USE_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String useYn = YesNo.Y;

    /**
     * 시/도인지 여부.
     *
     * @return 시/도면 {@code true}
     */
    public boolean isSido() {
        return level != null && level == LEVEL_SIDO;
    }

    /**
     * 시/군/구인지 여부. 프로필에 저장할 수 있는 지역은 이 단계뿐이다.
     *
     * @return 시/군/구면 {@code true}
     */
    public boolean isSigungu() {
        return level != null && level == LEVEL_SIGUNGU;
    }
}
