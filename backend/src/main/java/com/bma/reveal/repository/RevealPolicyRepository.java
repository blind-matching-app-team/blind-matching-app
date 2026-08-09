package com.bma.reveal.repository;

import com.bma.reveal.entity.RevealPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link RevealPolicy} 저장소.
 */
public interface RevealPolicyRepository extends JpaRepository<RevealPolicy, Integer> {

    /**
     * 사용 중인 공개 정책을 단계 오름차순으로 조회한다.
     *
     * @param useYn   사용 여부
     * @param deleted 논리 삭제 여부
     * @return 정책 목록
     */
    List<RevealPolicy> findByUseYnAndDeletedOrderByRevealLevelAsc(String useYn, String deleted);

    /**
     * 특정 단계의 정책을 조회한다.
     *
     * @param revealLevel 공개 단계
     * @param useYn       사용 여부
     * @param deleted     논리 삭제 여부
     * @return 정책
     */
    Optional<RevealPolicy> findByRevealLevelAndUseYnAndDeleted(Integer revealLevel, String useYn, String deleted);
}
