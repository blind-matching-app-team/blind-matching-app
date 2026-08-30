package com.bma.onboarding.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 온보딩 및 가치관 질문 마스터({@code ON_QUESTION}).
 */
@Entity
@Table(name = "ON_QUESTION")
@Getter
@Setter
@NoArgsConstructor
public class Question extends BaseAuditEntity {

    /** 질문 유형: 단일 선택. */
    public static final String TYPE_SINGLE = "SINGLE";

    /** 질문 유형: 복수 선택. */
    public static final String TYPE_MULTI = "MULTI";

    /** 질문 유형: 주관식. */
    public static final String TYPE_TEXT = "TEXT";

    /** 질문 유형: 척도(숫자). */
    public static final String TYPE_SCALE = "SCALE";

    /** 질문 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_ID")
    private Long id;

    /** 질문 유형(SINGLE/MULTI/TEXT/SCALE). */
    @Column(name = "QUESTION_TYPE", nullable = false)
    private String questionType = TYPE_SINGLE;

    /** 질문 분류 코드. */
    @Column(name = "CATEGORY_CODE", nullable = false)
    private String categoryCode;

    /** 질문 내용. */
    @Column(name = "QUESTION_TEXT", nullable = false, length = 500)
    private String questionText;

    /** 필수 응답 여부. */
    @Column(name = "REQUIRED_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String requiredYn = YesNo.N;

    /** 매칭 점수 계산 시 가중치. */
    @Column(name = "MATCH_WEIGHT", nullable = false)
    private BigDecimal matchWeight = BigDecimal.ONE;

    /** 노출 정렬 순서. */
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 진행률 표시용 구간 번호(1부터). {@code 0} 은 미배정.
     *
     * <p>화면의 "N/7" 은 문항 수가 아니라 구간 수 기준이다. 여러 문항이 한 구간에
     * 묶일 수 있어 정렬 순서와 별개로 관리한다. 프론트가 구간을 하드코딩하면
     * 문항이 바뀔 때마다 화면을 고쳐야 하므로 데이터로 내려준다.</p>
     */
    @Column(name = "STEP_NO", nullable = false)
    private Integer stepNo = 0;

    /** 사용 여부. */
    @Column(name = "USE_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String useYn = YesNo.Y;

    /**
     * 필수 응답 질문인지 확인한다.
     *
     * @return 필수이면 {@code true}
     */
    public boolean isRequired() {
        return YesNo.isY(requiredYn);
    }

    /**
     * 보기(옵션) 선택이 필요한 유형인지 확인한다.
     *
     * @return SINGLE 또는 MULTI이면 {@code true}
     */
    public boolean requiresOption() {
        return TYPE_SINGLE.equals(questionType) || TYPE_MULTI.equals(questionType);
    }
}
