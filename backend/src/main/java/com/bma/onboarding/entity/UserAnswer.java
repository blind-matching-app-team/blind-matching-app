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
 * 사용자의 온보딩/가치관 답변({@code ON_USER_ANSWER}).
 *
 * <p>{@code UK_ON_USER_ANSWER(USER_ID, QUESTION_ID, OPTION_ID)} 유니크 제약이 있어
 * 같은 조합을 두 번 삽입할 수 없다. 재제출 시에는 서비스가 기존 행을 갱신하거나
 * 물리 삭제 후 다시 넣어야 한다. 자세한 내용은
 * {@code com.bma.onboarding.service.OnboardingService}의 주석을 참고한다.</p>
 */
@Entity
@Table(name = "ON_USER_ANSWER")
@Getter
@Setter
@NoArgsConstructor
public class UserAnswer extends BaseAuditEntity {

    /** 답변 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ANSWER_ID")
    private Long id;

    /** 응답자 ID. */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 질문 ID. */
    @Column(name = "QUESTION_ID", nullable = false)
    private Long questionId;

    /** 선택한 보기 ID. 주관식/척도 질문에서는 {@code null}. */
    @Column(name = "OPTION_ID")
    private Long optionId;

    /** 주관식 답변. */
    @Column(name = "ANSWER_TEXT", length = 2000)
    private String answerText;

    /** 척도/숫자형 답변. */
    @Column(name = "ANSWER_NUMBER")
    private BigDecimal answerNumber;

    /**
     * 새 답변을 만든다.
     *
     * @param userId       응답자 ID
     * @param questionId   질문 ID
     * @param optionId     보기 ID(선택)
     * @param answerText   주관식 답변(선택)
     * @param answerNumber 숫자 답변(선택)
     * @return 저장 대상 엔티티
     */
    public static UserAnswer of(Long userId, Long questionId, Long optionId,
                                String answerText, BigDecimal answerNumber) {
        UserAnswer answer = new UserAnswer();
        answer.userId = userId;
        answer.questionId = questionId;
        answer.optionId = optionId;
        answer.answerText = answerText;
        answer.answerNumber = answerNumber;
        return answer;
    }
}
