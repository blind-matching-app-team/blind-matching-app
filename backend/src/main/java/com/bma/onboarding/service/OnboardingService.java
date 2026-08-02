package com.bma.onboarding.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.onboarding.dto.OnboardingDtos.AnswerItem;
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
                        optionsByQuestion.getOrDefault(question.getId(), List.of())))
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

        for (AnswerItem item : request.answers()) {
            String key = answerKey(item.questionId(), item.optionId());
            submittedKeys.add(key);

            UserAnswer answer = existingByKey.get(key);
            if (answer == null) {
                answer = UserAnswer.of(userId, item.questionId(), item.optionId(),
                        item.answerText(), item.answerNumber());
            } else {
                answer.setAnswerText(item.answerText());
                answer.setAnswerNumber(item.answerNumber());
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
        boolean completed = isOnboardingCompleted(userId);

        log.info("온보딩 답변 저장: userId={}, 저장={}건, 누적={}건, 완료={}",
                userId, toSave.size(), answeredCount, completed);
        return new AnswerResult(toSave.size(), answeredCount, completed);
    }

    /**
     * 필수 질문에 모두 답했는지 확인한다.
     *
     * @param userId 사용자 ID
     * @return 필수 질문을 모두 채웠으면 {@code true}
     */
    public boolean isOnboardingCompleted(Long userId) {
        Set<Long> requiredQuestionIds = questionRepository
                .findByUseYnAndDeletedOrderBySortOrderAsc(YesNo.Y, YesNo.N).stream()
                .filter(Question::isRequired)
                .map(Question::getId)
                .collect(Collectors.toSet());

        if (requiredQuestionIds.isEmpty()) {
            return true;
        }

        Set<Long> answeredQuestionIds = answerRepository.findByUserIdAndDeleted(userId, YesNo.N).stream()
                .map(UserAnswer::getQuestionId)
                .collect(Collectors.toSet());

        return answeredQuestionIds.containsAll(requiredQuestionIds);
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
