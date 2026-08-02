package com.bma.matching.repository;

import com.bma.matching.entity.MatchQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link MatchQueue} 저장소.
 */
public interface MatchQueueRepository extends JpaRepository<MatchQueue, Long> {

    /**
     * 사용자의 대기 중인 항목을 조회한다.
     *
     * @param userId  사용자 ID
     * @param status  대기 상태
     * @param deleted 논리 삭제 여부
     * @return 대기열 항목
     */
    Optional<MatchQueue> findFirstByUserIdAndQueueStatusAndDeleted(Long userId, String status, String deleted);
}
