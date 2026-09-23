package com.bma.verification.repository;

import com.bma.verification.entity.PhotoVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link PhotoVerification} 저장소.
 */
public interface PhotoVerificationRepository extends JpaRepository<PhotoVerification, Long> {

    long countByUserIdAndDeleted(Long userId, String deleted);

    Optional<PhotoVerification> findFirstByUserIdAndDeletedOrderByIdDesc(Long userId, String deleted);
}
