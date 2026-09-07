package com.bma.onboarding.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.onboarding.dto.OnboardingDtos.AnswerItem;
import com.bma.onboarding.dto.OnboardingDtos.OptionView;
import com.bma.onboarding.dto.OnboardingDtos.AnswerRequest;
import com.bma.onboarding.dto.OnboardingDtos.AnswerResult;
import com.bma.onboarding.dto.OnboardingDtos.QuestionView;
import com.bma.onboarding.entity.Question;
import com.bma.onboarding.entity.QuestionOption;
import com.bma.onboarding.entity.UserAnswer;
import com.bma.onboarding.repository.QuestionOptionRepository;
import com.bma.onboarding.repository.QuestionRepository;
import com.bma.onboarding.repository.UserAnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 온보딩 질문 조회 및 답변 저장.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li><b>재제출 시 500 오류 해결</b>: {@code UK_ON_USER_ANSWER(USER_ID, QUESTION_ID, OPTION_ID)}
 *       제약 때문에 기존 구현은 두 번째 제출에서 무조건 실패했다. 이제 기존 답변을 조회해
 *       갱신/복구하고, 빠진 항목만 논리 삭제하는 방식으로 처리한다.
 *       (조회 시 논리 삭제 여부를 걸지 않는 이유는, 유니크 제약이 {@code DELETED}를 포함하지 않아
 *        삭제된 행도 같은 조합의 재삽입을 막기 때문이다.)</li>
 *   <li>질문/보기 존재 여부와 소속 관계를 검증한다(기존에는 임의의 ID를 그대로 저장했다).</li>
 *   <li>질문 유형별 필수 입력을 검증한다.</li>
 *   <li>반복문 안에서 건건이 save 하던 것을 일괄 저장으로 바꿨다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final UserAnswerRepository answerRepository;

    /**
     * 사용 중인 온보딩 질문과 보기를 조회한다.
     *
     * @return 정렬된 질문 목록
     */
    public List<QuestionView> getQuestions() {
        List<Question> questions = questionRepository
                .findByUseYnAndDeletedOrderBySortOrderAsc(YesNo.Y, YesNo.N);
        if (questions.isEmpty()) {
            return List.of();
        }

        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        // 질문마다 보기를 조회하면 N+1이 되므로 한 번에 가져와 메모리에서 묶는다.
        Map<Long, List<QuestionOption>> optionsByQuestion = optionRepository
                .findByQuestionIdInAndDeletedOrderBySortOrderAsc(questionIds, YesNo.N).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));

        return questions.stream()
                .map(question -> QuestionView.of(question,
                        toOptionTree(optionsByQuestion.getOrDefault(question.getId(), List.of()))))
                .toList();
    }

    /**
     * 평면 보기 목록을 대분류 → 세부 2단계 구조로 조립한다.
     *
     * <p>관심사 문항은 대분류(문화생활, 여행 등) 아래 세부 항목을 갖는다
     * (S2 사양서 v1.4 의 S2-10/S2-11). 계층이 없는 일반 문항은 모든 보기가
     * 최상위라 기존과 같은 평면 목록이 된다.</p>
     *
     * @param options 한 질문의 전체 보기(정렬된 상태)
     * @return 최상위 보기 목록. 각 항목의 {@code children} 에 세부 보기가 담긴다
     */
    private List<OptionView> toOptionTree(List<QuestionOption> options) {
        Map<Long, List<QuestionOption>> childrenByParent = options.stream()
                .filter(option -> !option.isTopLevel())
                .collect(Collectors.groupingBy(QuestionOption::getParentOptionId));

        return options.stream()
                .filter(QuestionOption::isTopLevel)
                .map(parent -> OptionView.of(parent,
                        childrenByParent.getOrDefault(parent.getId(), List.of()).stream()
                                .map(OptionView::from)
                                .toList()))
                .toList();
    }

    /**
     * 답변을 저장한다. 같은 질문에 대해 다시 제출하면 기존 답변을 대체한다.
     *
     * @param userId  응답자 ID
     * @param request 답변 요청
     * @return 저장 결과
     * @throws BusinessException 존재하지 않는 질문/보기이거나 유형별 필수 입력이 빠진 경우
     */
    @Transactional
    public AnswerResult saveAnswers(Long userId, AnswerRequest request) {
        List<Long> questionIds = request.answers().stream()
                .map(AnswerItem::questionId)
                .distinct()
                .toList();

        Map<Long, Question> questions = loadActiveQuestions(questionIds);
        Map<Long, QuestionOption> options = loadOptions(request.answers());

        validate(request.answers(), questions, options);

        // 유니크 제약은 DELETED를 포함하지 않으므로, 삭제된 행까지 포함해서 조회해야
        // 같은 (사용자, 질문, 보기) 조합을 안전하게 재사용할 수 있다.
        Map<String, UserAnswer> existingByKey = answerRepository
                .findByUserIdAndQuestionIdIn(userId, questionIds).stream()
                .collect(Collectors.toMap(
                        answer -> answerKey(answer.getQuestionId(), answer.getOptionId()),
                        Function.identity(),
                        // 과거 데이터에 중복이 있어도 하나만 살려 두면 되므로 첫 번째를 유지한다.
                        (first, second) -> first));

        List<UserAnswer> toSave = new ArrayList<>();
        Set<String> submittedKeys = new HashSet<>();

        for (AnswerItem item : deduplicate(request.answers())) {
            String key = answerKey(item.questionId(), item.optionId());
            submittedKeys.add(key);

            UserAnswer answer = existingByKey.get(key);
            if (answer == null) {
                answer = UserAnswer.of(userId, item.questionId(), item.optionId(),
                        item.answerText(), item.answerNumber(), item.rank());
            } else {
                answer.setAnswerText(item.answerText());
                answer.setAnswerNumber(item.answerNumber());
                // 우선순위를 다시 매길 수 있으므로 매번 갱신한다.
                answer.setAnswerRank(item.rank());
                // 이전에 지웠던 답변을 다시 선택한 경우 되살린다.
                answer.restore();
            }
            toSave.add(answer);
        }

        // 이번 제출에 포함되지 않은 기존 답변은 논리 삭제한다.
        // (물리 삭제하지 않는 이유: 응답 변경 이력을 남겨 두기 위함)
        existingByKey.forEach((key, answer) -> {
            if (!submittedKeys.contains(key)) {
                answer.markDeleted();
            }
        });

        answerRepository.saveAll(toSave);

        long answeredCount = answerRepository.countByUserIdAndDeleted(userId, YesNo.N);
        StepProgress progress = calculateProgress(userId);

        log.info("온보딩 답변 저장: userId={}, 저장={}건, 누적={}건, 진행={}/{} ({}%)",
                userId, toSave.size(), answeredCount,
                progress.completedSteps(), progress.totalSteps(), progress.rate());
        return new AnswerResult(toSave.size(), answeredCount, progress.completed(),
                progress.totalSteps(), progress.completedSteps(), progress.rate());
    }

    /**
     * 필수 질문에 모두 답했는지 확인한다.
     *
     * <p>질문이 하나도 없으면 완료로 보지 않는다. 콘텐츠가 아직 입력되지 않은 상태를
     * "온보딩 완료"로 판정하면 신규 사용자가 설문을 건너뛴 채 매칭에 들어간다.</p>
     *
     * @param userId 사용자 ID
     * @return 필수 질문을 모두 채웠으면 {@code true}
     */
    public boolean isOnboardingCompleted(Long userId) {
        return calculateProgress(userId).completed();
    }

    /**
     * 구간 기준 진행 상황.
     *
     * @param totalSteps     전체 구간 수
     * @param completedSteps 필수 문항을 모두 채운 구간 수
     * @param completed      전체 완료 여부
     */
    public record StepProgress(int totalSteps, int completedSteps, boolean completed) {

        /**
         * 완료율을 백분율로 계산한다.
         *
         * @return 0~100. 구간이 없으면 0
         */
        public int rate() {
            return totalSteps == 0 ? 0 : (int) Math.round(completedSteps * 100.0 / totalSteps);
        }
    }

    /**
     * 사용자의 온보딩 진행 상황을 구간 기준으로 계산한다.
     *
     * <p>화면의 "N/7" 은 문항 22개가 아니라 그룹 순번이다(S2 사양서 v1.4).
     * 그래서 문항 수가 아니라 {@code STEP_NO} 로 묶어서 센다. 한 구간은 그 안의
     * <b>필수 문항을 모두</b> 채워야 완료로 본다.</p>
     *
     * <p>구간이 배정되지 않은 문항({@code STEP_NO=0})만 있는 경우에는 문항 하나를
     * 한 구간으로 취급한다. 콘텐츠 확정 전에도 진행률이 동작하도록 하기 위함이다.</p>
     *
     * @param userId 사용자 ID
     * @return 진행 상황
     */
    public StepProgress calculateProgress(Long userId) {
        List<Question> required = questionRepository
                .findByUseYnAndDeletedOrderBySortOrderAsc(YesNo.Y, YesNo.N).stream()
                .filter(Question::isRequired)
                .toList();

        if (required.isEmpty()) {
            // 질문 콘텐츠가 아직 없는 상태. 완료로 보면 설문을 건너뛰게 되므로 미완료로 둔다.
            return new StepProgress(0, 0, false);
        }

        Set<Long> answeredQuestionIds = answerRepository.findByUserIdAndDeleted(userId, YesNo.N).stream()
                .map(UserAnswer::getQuestionId)
                .collect(Collectors.toSet());

        // STEP_NO 가 배정되지 않았으면 문항 ID 를 구간 키로 삼아 문항 단위로 센다.
        Map<Object, List<Question>> byStep = required.stream()
                .collect(Collectors.groupingBy(question ->
                        question.getStepNo() != null && question.getStepNo() > 0
                                ? "step:" + question.getStepNo()
                                : "question:" + question.getId()));

        int completedSteps = (int) byStep.values().stream()
                .filter(group -> group.stream()
                        .allMatch(question -> answeredQuestionIds.contains(question.getId())))
                .count();

        int totalSteps = byStep.size();
        return new StepProgress(totalSteps, completedSteps, completedSteps == totalSteps);
    }

    /**
     * 요청에 포함된 질문 ID가 모두 사용 중인 질문인지 확인하며 조회한다.
     *
     * @param questionIds 질문 ID 목록
     * @return 질문 ID → 질문 맵
     * @throws BusinessException 존재하지 않거나 비활성 질문이 포함된 경우
     */
    private Map<Long, Question> loadActiveQuestions(List<Long> questionIds) {
        Map<Long, Question> questions = questionRepository
                .findByIdInAndUseYnAndDeleted(questionIds, YesNo.Y, YesNo.N).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        if (questions.size() != questionIds.size()) {
            List<Long> missing = questionIds.stream().filter(id -> !questions.containsKey(id)).toList();
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "유효하지 않은 질문입니다: " + missing);
        }
        return questions;
    }

    /**
     * 요청에 포함된 보기 ID를 조회한다.
     *
     * @param answers 답변 목록
     * @return 보기 ID → 보기 맵
     */
    private Map<Long, QuestionOption> loadOptions(List<AnswerItem> answers) {
        List<Long> optionIds = answers.stream()
                .map(AnswerItem::optionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (optionIds.isEmpty()) {
            return Map.of();
        }
        return optionRepository.findByIdInAndDeleted(optionIds, YesNo.N).stream()
                .collect(Collectors.toMap(QuestionOption::getId, Function.identity()));
    }

    /**
     * 질문 유형별 필수 입력과 보기 소속 관계를 검증한다.
     *
     * @param answers   답변 목록
     * @param questions 질문 맵
     * @param options   보기 맵
     * @throws BusinessException 검증 실패 시
     */
    private void validate(List<AnswerItem> answers,
                          Map<Long, Question> questions,
                          Map<Long, QuestionOption> options) {

        Map<Long, Integer> answerCountByQuestion = new HashMap<>();

        for (AnswerItem item : answers) {
            Question question = questions.get(item.questionId());
            answerCountByQuestion.merge(item.questionId(), 1, Integer::sum);

            if (question.requiresOption()) {
                if (item.optionId() == null) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                            "선택형 질문에는 보기를 선택해야 합니다. questionId=" + item.questionId());
                }
                QuestionOption option = options.get(item.optionId());
                if (option == null) {
                    throw new BusinessException(ErrorCode.OPTION_MISMATCH,
                            "존재하지 않는 보기입니다. optionId=" + item.optionId());
                }
                // 다른 질문의 보기를 끼워 넣는 것을 막는다.
                if (!option.getQuestionId().equals(item.questionId())) {
                    throw new BusinessException(ErrorCode.OPTION_MISMATCH,
                            "보기가 해당 질문에 속하지 않습니다. questionId=" + item.questionId()
                                    + ", optionId=" + item.optionId());
                }
            } else if (Question.TYPE_TEXT.equals(question.getQuestionType())) {
                if (item.answerText() == null || item.answerText().isBlank()) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                            "주관식 질문에는 답변 내용이 필요합니다. questionId=" + item.questionId());
                }
            } else if (Question.TYPE_SCALE.equals(question.getQuestionType())
                    && item.answerNumber() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "척도 질문에는 숫자 답변이 필요합니다. questionId=" + item.questionId());
            }
        }

        // 단일 선택 질문에 두 개 이상의 보기가 들어오면 데이터가 모순된다.
        answerCountByQuestion.forEach((questionId, count) -> {
            Question question = questions.get(questionId);
            if (Question.TYPE_SINGLE.equals(question.getQuestionType()) && count > 1) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "단일 선택 질문에는 하나의 보기만 선택할 수 있습니다. questionId=" + questionId);
            }
        });
    }

    /**
     * 같은 (질문, 보기) 조합이 여러 번 들어온 경우 마지막 것만 남긴다.
     *
     * <p>답변은 본질적으로 선택 집합이라 중복은 의미가 없다. 그대로 두면 같은 키로
     * 두 행을 저장하려다 {@code UK_ON_USER_ANSWER} 유니크 제약에 걸려
     * "이미 처리된 요청" 이라는 엉뚱한 오류가 나간다. 클라이언트가 같은 보기를
     * 두 번 보낸 것뿐이므로 조용히 정리하는 편이 낫다.</p>
     *
     * <p>마지막 것을 남기는 이유: 우선순위를 다시 매긴 경우 뒤에 온 값이 최신이다.</p>
     *
     * @param answers 원본 답변 목록
     * @return 중복이 제거된 목록. 입력 순서는 유지된다
     */
    private List<AnswerItem> deduplicate(List<AnswerItem> answers) {
        Map<String, AnswerItem> byKey = new LinkedHashMap<>();
        for (AnswerItem item : answers) {
            byKey.put(answerKey(item.questionId(), item.optionId()), item);
        }
        if (byKey.size() != answers.size()) {
            log.debug("중복된 답변 {}건을 정리했다.", answers.size() - byKey.size());
        }
        return List.copyOf(byKey.values());
    }

    /**
     * 유니크 제약과 동일한 기준(질문 + 보기)으로 답변 식별 키를 만든다.
     *
     * @param questionId 질문 ID
     * @param optionId   보기 ID({@code null} 허용)
     * @return 식별 키
     */
    private String answerKey(Long questionId, Long optionId) {
        return questionId + ":" + (optionId == null ? "-" : optionId);
    }
}
