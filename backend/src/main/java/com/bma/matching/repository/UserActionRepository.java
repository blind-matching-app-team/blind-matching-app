package com.bma.matching.repository;

import com.bma.matching.entity.UserAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link UserAction} 저장소.
 */
public interface UserActionRepository extends JpaRepository<UserAction, Long> {

    /**
     * 특정 방향의 액션을 조회한다(삭제 이력 포함).
     *
     * <p>{@code UK_MT_USER_ACTION(FROM_USER_ID, TO_USER_ID)} 제약 때문에 같은 조합은
     * 재삽입할 수 없다. 기존 행을 찾아 갱신해야 하므로 논리 삭제 여부로 거르지 않는다.</p>
     *
     * @param fromUserId 행동 주체
     * @param toUserId   대상
     * @return 액션
     */
    Optional<UserAction> findByFromUserIdAndToUserId(Long fromUserId, Long toUserId);

    /**
     * 내가 액션을 남긴 상대들의 ID를 조회한다. 추천 목록에서 제외하는 데 사용한다.
     *
     * @param fromUserId 행동 주체
     * @param deleted    논리 삭제 여부
     * @return 대상 사용자 ID 목록
     */
    @Query("select a.toUserId from UserAction a where a.fromUserId = :fromUserId and a.deleted = :deleted")
    List<Long> findActedUserIds(@Param("fromUserId") Long fromUserId, @Param("deleted") String deleted);

    /**
     * 나에게 호감을 표시한 사용자 수를 센다.
     *
     * @param toUserId    대상(나)
     * @param actionTypes 호감으로 간주할 액션 유형
     * @param deleted     논리 삭제 여부
     * @return 건수
     */
    long countByToUserIdAndActionTypeInAndDeleted(Long toUserId, List<String> actionTypes, String deleted);
}
