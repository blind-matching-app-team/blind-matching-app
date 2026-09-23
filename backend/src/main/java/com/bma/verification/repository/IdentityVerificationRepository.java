package com.bma.verification.repository;

import com.bma.verification.entity.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link IdentityVerification} 저장소.
 */
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

    Optional<IdentityVerification> findByTransactionIdAndDeleted(String transactionId, String deleted);

    /** 사용자의 아직 열려 있는 요청들(재요청 시 만료 처리용). */
    List<IdentityVerification> findByUserIdAndStatusAndDeleted(Long userId, String status, String deleted);

    /** 사용자의 최근 시도(상태 조회·S15 거부 화면 근거). */
    Optional<IdentityVerification> findFirstByUserIdAndDeletedOrderByIdDesc(Long userId, String deleted);
}
