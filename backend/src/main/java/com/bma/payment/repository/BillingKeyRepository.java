package com.bma.payment.repository;

import com.bma.payment.entity.BillingKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link BillingKey} 저장소.
 */
public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {

    /**
     * 사용자의 빌링키 행(사용자당 1행).
     *
     * @param userId 사용자
     * @return 빌링키
     */
    Optional<BillingKey> findByUserId(Long userId);
}
