package com.bma.payment.repository;

import com.bma.payment.entity.ItemLedger;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link ItemLedger} 저장소.
 */
public interface ItemLedgerRepository extends JpaRepository<ItemLedger, Long> {

    /**
     * 같은 달 지급이 이미 됐는지 확인한다(배치 재실행 방어).
     *
     * @param grantKey 월 지급 멱등 키
     * @return 있으면 {@code true}
     */
    boolean existsByGrantKey(String grantKey);
}
