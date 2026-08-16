package com.bma.onboarding.repository;

import com.bma.onboarding.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@link Question} 저장소.
 */
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * 사용 중인 질문을 정렬 순서대로 조회한다.
     *
     * @param useYn   사용 여부
     * @param deleted 논리 삭제 여부
     * @return 질문 목록
     */
    List<Question> findByUseYnAndDeletedOrderBySortOrderAsc(String useYn, String deleted);

    /**
     * 지정한 ID들 중 사용 중인 질문만 조회한다.
     *
     * <p>답변 저장 시 존재하지 않거나 비활성화된 질문 ID가 들어오는 것을 걸러내는 데 사용한다.</p>
     *
     * @param ids     질문 ID 목록
     * @param useYn   사용 여부
     * @param deleted 논리 삭제 여부
     * @return 질문 목록
     */
    List<Question> findByIdInAndUseYnAndDeleted(List<Long> ids, String useYn, String deleted);
}
