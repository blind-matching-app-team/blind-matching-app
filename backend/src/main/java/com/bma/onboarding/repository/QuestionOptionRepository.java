package com.bma.onboarding.repository;

import com.bma.onboarding.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@link QuestionOption} 저장소.
 */
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    /**
     * 여러 질문의 보기를 한 번에 조회한다.
     *
     * <p>질문마다 보기를 따로 조회하면 N+1이 되므로 IN 조건으로 한 번에 가져온다.</p>
     *
     * @param questionIds 질문 ID 목록
     * @param deleted     논리 삭제 여부
     * @return 보기 목록
     */
    List<QuestionOption> findByQuestionIdInAndDeletedOrderBySortOrderAsc(List<Long> questionIds, String deleted);

    /**
     * 지정한 보기 ID들을 조회한다. 답변 검증 시 보기의 소속 질문을 확인하는 데 사용한다.
     *
     * @param ids     보기 ID 목록
     * @param deleted 논리 삭제 여부
     * @return 보기 목록
     */
    List<QuestionOption> findByIdInAndDeleted(List<Long> ids, String deleted);
}
