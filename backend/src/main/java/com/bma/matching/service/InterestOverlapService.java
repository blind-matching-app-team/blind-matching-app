package com.bma.matching.service;

import com.bma.common.entity.YesNo;
import com.bma.onboarding.entity.Question;
import com.bma.onboarding.entity.UserAnswer;
import com.bma.onboarding.repository.QuestionRepository;
import com.bma.onboarding.repository.UserAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 두 사용자의 공통 관심사 개수. 정밀매칭(구독 혜택)이 대기열 후보 중 더 잘 맞는 상대를 고를 때 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestOverlapService {

    private final QuestionRepository questionRepository;
    private final UserAnswerRepository answerRepository;

    /**
     * 공통으로 고른 관심사 보기 수.
     *
     * @param userIdA 사용자 A
     * @param userIdB 사용자 B
     * @return 공통 개수. 관심사 문항이 없으면 0
     */
    public int commonCount(Long userIdA, Long userIdB) {
        List<Long> questionIds = questionRepository
                .findByCategoryCodeAndUseYnAndDeleted(Question.CATEGORY_INTEREST, YesNo.Y, YesNo.N).stream()
                .map(Question::getId)
                .toList();
        if (questionIds.isEmpty()) {
            return 0;
        }
        Set<Long> a = selected(userIdA, questionIds);
        Set<Long> common = new HashSet<>(selected(userIdB, questionIds));
        common.retainAll(a);
        return common.size();
    }

    private Set<Long> selected(Long userId, List<Long> questionIds) {
        return answerRepository.findByUserIdAndQuestionIdIn(userId, questionIds).stream()
                .filter(answer -> !answer.isDeleted() && answer.getOptionId() != null)
                .map(UserAnswer::getOptionId)
                .collect(Collectors.toSet());
    }
}
