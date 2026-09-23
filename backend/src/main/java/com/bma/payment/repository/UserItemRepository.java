package com.bma.payment.repository;

import com.bma.payment.entity.UserItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link UserItem} 저장소.
 */
public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    /**
     * 사용자의 이용권 잔여 행을 모두 가져온다.
     *
     * @param userId 사용자
     * @return 잔여 행(종류별 최대 1건)
     */
    List<UserItem> findByUserIdOrderByItemTypeAsc(Long userId);

    /**
     * 증감을 위해 행 잠금을 걸고 가져온다. 같은 사용자가 동시에 두 번 사용해도 잔여가 음수가 되지 않게 한다.
     *
     * @param userId   사용자
     * @param itemType 이용권 종류
     * @return 잔여 행
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserItem i where i.userId = :userId and i.itemType = :itemType")
    Optional<UserItem> findForUpdate(@Param("userId") Long userId, @Param("itemType") String itemType);
}
