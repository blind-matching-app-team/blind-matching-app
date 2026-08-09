package com.bma.onboarding.dto;

import com.bma.onboarding.entity.Question;
import com.bma.onboarding.entity.QuestionOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 온보딩 API의 요청/응답 DTO 모음.
 *
 * <p>기존에는 {@code QuestionOption} 엔티티를 그대로 응답에 담아
 * 감사 컬럼과 내부 점수({@code SCORE_VALUE})까지 노출되고 있었다.
 * 점수는 매칭 알고리즘 내부 값이므로 클라이언트에 내려주지 않는다.</p>
 */
public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    /**
     * 질문 보기 응답.
     *
     * @param optionId   보기 ID
     * @param optionCode 보기 코드
     * @param optionText 보기 내용
     * @param sortOrder  정렬 순서
     */
    public record OptionView(Long optionId, String optionCode, String optionText, Integer sortOrder) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param option 보기 엔티티
         * @return 응답 DTO
         */
        public static OptionView from(QuestionOption option) {
            return new OptionView(option.getId(), option.getOptionCode(),
                    option.getOptionText(), option.getSortOrder());
        }
    }

    /**
     * 질문 응답.
     *
     * @param questionId   질문 ID
     * @param questionText 질문 내용
     * @param questionType 질문 유형
     * @param categoryCode 분류 코드
     * @param required     필수 여부
     * @param sortOrder    정렬 순서
     * @param options      보기 목록(주관식/척도는 빈 목록)
     */
    public record QuestionView(Long questionId,
                               String questionText,
                               String questionType,
                               String categoryCode,
                               boolean required,
                               Integer sortOrder,
                               List<OptionView> options) {

        /**
         * 엔티티와 보기 목록을 응답 DTO로 변환한다.
         *
         * @param question 질문 엔티티
         * @param options  해당 질문의 보기 목록
         * @return 응답 DTO
         */
        public static QuestionView of(Question question, List<QuestionOption> options) {
            return new QuestionView(
                    question.getId(),
                    question.getQuestionText(),
                    question.getQuestionType(),
                    question.getCategoryCode(),
                    question.isRequired(),
                    question.getSortOrder(),
                    options.stream().map(OptionView::from).toList());
        }
    }

    /**
     * 답변 1건.
     *
     * @param questionId   질문 ID
     * @param optionId     선택한 보기 ID(선택형에서 필수)
     * @param answerText   주관식 답변
     * @param answerNumber 척도 답변
     */
    public record AnswerItem(
            @NotNull(message = "질문 ID는 필수입니다.") Long questionId,
            Long optionId,
            @Size(max = 2000, message = "주관식 답변은 2000자를 넘을 수 없습니다.") String answerText,
            BigDecimal answerNumber
    ) {
    }

    /**
     * 답변 제출 요청.
     *
     * @param answers 답변 목록
     */
    public record AnswerRequest(
            @NotEmpty(message = "답변이 비어 있습니다.")
            @Valid List<AnswerItem> answers
    ) {
    }

    /**
     * 답변 제출 결과.
     *
     * @param savedCount    저장된 답변 수
     * @param answeredCount 사용자가 지금까지 답변한 총 건수
     * @param completed     필수 질문을 모두 채웠는지 여부
     */
    public record AnswerResult(int savedCount, long answeredCount, boolean completed) {
    }
}
