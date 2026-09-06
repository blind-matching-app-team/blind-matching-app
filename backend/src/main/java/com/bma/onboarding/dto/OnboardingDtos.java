package com.bma.onboarding.dto;

import com.bma.onboarding.entity.Question;
import com.bma.onboarding.entity.QuestionOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    public record OptionView(Long optionId,
                             String optionCode,
                             String optionText,
                             Integer sortOrder,
                             List<OptionView> children) {

        /**
         * 하위 보기가 없는 일반 보기로 변환한다.
         *
         * @param option 보기 엔티티
         * @return 응답 DTO
         */
        public static OptionView from(QuestionOption option) {
            return of(option, List.of());
        }

        /**
         * 하위 보기를 포함해 변환한다. 관심사 대분류가 세부 항목을 갖는 경우에 쓴다.
         *
         * @param option   보기 엔티티
         * @param children 하위 보기 목록
         * @return 응답 DTO
         */
        public static OptionView of(QuestionOption option, List<OptionView> children) {
            return new OptionView(option.getId(), option.getOptionCode(),
                    option.getOptionText(), option.getSortOrder(), children);
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
     * @param stepNo       진행률 표시용 구간 번호(1부터, 0은 미배정)
     * @param options      보기 목록(주관식/척도는 빈 목록).
     *                     관심사처럼 계층이 있으면 대분류만 담기고 세부는 {@code children} 에 들어간다
     */
    public record QuestionView(Long questionId,
                               String questionText,
                               String questionType,
                               String categoryCode,
                               boolean required,
                               Integer sortOrder,
                               Integer stepNo,
                               List<OptionView> options) {

        /**
         * 엔티티와 보기 목록을 응답 DTO로 변환한다.
         *
         * @param question 질문 엔티티
         * @param options  해당 질문의 보기 목록
         * @return 응답 DTO
         */
        public static QuestionView of(Question question, List<OptionView> options) {
            return new QuestionView(
                    question.getId(),
                    question.getQuestionText(),
                    question.getQuestionType(),
                    question.getCategoryCode(),
                    question.isRequired(),
                    question.getSortOrder(),
                    question.getStepNo(),
                    options);
        }
    }

    /**
     * 답변 1건.
     *
     * @param questionId   질문 ID
     * @param optionId     선택한 보기 ID(선택형에서 필수)
     * @param answerText   주관식 답변
     * @param answerNumber 척도 답변
     * @param rank         우선순위(1부터). 관심사 대분류 정렬에만 쓰고, 없으면 {@code null}
     */
    public record AnswerItem(
            @NotNull(message = "질문 ID는 필수입니다.") Long questionId,
            Long optionId,
            @Size(max = 2000, message = "주관식 답변은 2000자를 넘을 수 없습니다.") String answerText,
            BigDecimal answerNumber,
            @Positive(message = "우선순위는 1 이상이어야 합니다.") Integer rank
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
     * <p>진행률은 <b>문항 수가 아니라 구간(step) 수</b> 기준이다. 화면의 "N/7" 표기가
     * 문항 22개가 아니라 그룹 순번을 가리키기 때문이다(S2 사양서 v1.4).
     * 구간이 배정되지 않은 문항만 있으면 문항 수로 대체한다.</p>
     *
     * @param savedCount     이번 요청으로 저장된 답변 수
     * @param answeredCount  사용자가 지금까지 답변한 총 건수
     * @param completed      필수 질문을 모두 채웠는지 여부
     * @param totalSteps     전체 구간 수
     * @param completedSteps 필수 문항을 모두 채운 구간 수
     * @param completionRate 완료율(0~100, 소수점 없음)
     */
    public record AnswerResult(int savedCount,
                               long answeredCount,
                               boolean completed,
                               int totalSteps,
                               int completedSteps,
                               int completionRate) {
    }
}
