package com.bma.matching.repository;

import com.bma.matching.entity.MatchQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 특정 시각 이후 특정 재원으로 진입한 횟수(하루 무료 기회 계산).
     *
     * @param userId      사용자
     * @param since       기준 시각(오늘 0시)
     * @param entrySource 진입 재원
     * @param deleted     논리 삭제 여부
     * @return 횟수
     */
    long countByUserIdAndEnterDateGreaterThanEqualAndEntrySourceAndDeleted(Long userId, LocalDateTime since,
                                                                           String entrySource, String deleted);

    /**
     * 사용자의 가장 최근 대기열 항목(상태 무관). S9 폴링의 기준이다.
     */
    Optional<MatchQueue> findFirstByUserIdAndDeletedOrderByIdDesc(Long userId, String deleted);

    /**
     * 특정 상태의 항목을 진입 순서(FIFO)로 가져온다.
     */
    List<MatchQueue> findByQueueStatusAndDeletedOrderByEnterDateAscIdAsc(String status, String deleted);

    /**
     * 짝을 지을 때 상태 재확인용 행 잠금.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from MatchQueue q where q.id = :id")
    Optional<MatchQueue> findForUpdate(@Param("id") Long id);

    /**
     * 특정 시각 이후 특정 재원으로 진입한 횟수 중 특정 상태(만료)가 아닌 것(하루 무료 기회 계산).
     */
    long countByUserIdAndEnterDateGreaterThanEqualAndEntrySourceAndQueueStatusNotAndDeleted(
            Long userId, LocalDateTime since, String entrySource, String excludedStatus, String deleted);
}
