package com.bma.onboarding.entity;

import com.bma.common.entity.BaseAuditEntity;
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
 * 선택형 질문의 보기({@code ON_QUESTION_OPTION}).
 */
@Entity
@Table(name = "ON_QUESTION_OPTION")
@Getter
@Setter
@NoArgsConstructor
public class QuestionOption extends BaseAuditEntity {

    /** 보기 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OPTION_ID")
    private Long id;

    /** 소속 질문 ID. */
    @Column(name = "QUESTION_ID", nullable = false)
    private Long questionId;

    /**
     * 상위 보기 ID. 세부 관심사가 대분류 보기를 가리킨다. 일반 보기는 {@code null}.
     *
     * <p>예: "문화생활"(대분류) 아래 "영화", "전시/미술관", "독서"(세부).</p>
     */
    @Column(name = "PARENT_OPTION_ID")
    private Long parentOptionId;

    /** 보기 코드. 질문 내에서 유일하다. */
    @Column(name = "OPTION_CODE", nullable = false)
    private String optionCode;

    /** 보기 내용. */
    @Column(name = "OPTION_TEXT", nullable = false, length = 300)
    private String optionText;

    /** 매칭 알고리즘 계산용 값. */
    @Column(name = "SCORE_VALUE")
    private BigDecimal scoreValue;

    /** 노출 정렬 순서. */
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 대분류(최상위) 보기인지 확인한다.
     *
     * @return 부모가 없으면 {@code true}
     */
    public boolean isTopLevel() {
        return parentOptionId == null;
    }
}
