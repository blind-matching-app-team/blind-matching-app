package com.bma.payment.repository;

import com.bma.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link Subscription} 저장소.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * 사용자의 종료되지 않은 구독(ACTIVE/PAST_DUE/CANCELED). 최대 1건이어야 한다.
     *
     * @param userId   사용자
     * @param statuses 살아 있는 상태 집합
     * @param deleted  논리 삭제 여부
     * @return 구독
     */
    Optional<Subscription> findFirstByUserIdAndSubStatusInAndDeletedOrderByIdDesc(
            Long userId, Collection<String> statuses, String deleted);

    /**
     * 사용자의 가장 최근 구독(종료된 것 포함).
     *
     * @param userId  사용자
     * @param deleted 논리 삭제 여부
     * @return 구독
     */
    Optional<Subscription> findFirstByUserIdAndDeletedOrderByIdDesc(Long userId, String deleted);

    /**
     * 청구일이 지난 구독(ACTIVE/PAST_DUE).
     *
     * @param asOf 기준일
     * @return 청구 대상
     */
    @Query("""
            select s from Subscription s
             where s.subStatus in ('ACTIVE', 'PAST_DUE')
               and s.nextBillingDate <= :asOf
               and s.deleted = 'N'
             order by s.id asc
            """)
    List<Subscription> findDueForBilling(@Param("asOf") LocalDate asOf);

    /**
     * 해지 후 이용 기간이 끝난 구독.
     *
     * @param asOf 기준일
     * @return 종료 대상
     */
    @Query("""
            select s from Subscription s
             where s.subStatus = 'CANCELED'
               and s.currentPeriodEnd < :asOf
               and s.deleted = 'N'
             order by s.id asc
            """)
    List<Subscription> findCanceledAndEnded(@Param("asOf") LocalDate asOf);
}
