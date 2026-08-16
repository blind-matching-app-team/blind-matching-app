package com.bma.onboarding.repository;

import com.bma.onboarding.entity.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@link UserAnswer} 저장소.
 */
public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {

    /**
     * 사용자의 모든 답변을 조회한다.
     *
     * @param userId  사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 답변 목록
     */
    List<UserAnswer> findByUserIdAndDeleted(Long userId, String deleted);

    /**
     * 사용자의 특정 질문에 대한 답변을 조회한다.
     *
     * @param userId      사용자 ID
     * @param questionIds 질문 ID 목록
     * @return 답변 목록
     */
    List<UserAnswer> findByUserIdAndQuestionIdIn(Long userId, List<Long> questionIds);

    /**
     * 사용자가 답변한 서로 다른 질문의 수를 센다. 온보딩 완료 여부 판정에 사용한다.
     *
     * @param userId  사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 답변 행 수
     */
    long countByUserIdAndDeleted(Long userId, String deleted);
}
