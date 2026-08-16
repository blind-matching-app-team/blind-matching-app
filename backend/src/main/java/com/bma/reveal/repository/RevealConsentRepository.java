package com.bma.reveal.repository;

import com.bma.reveal.entity.RevealConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link RevealConsent} 저장소.
 */
public interface RevealConsentRepository extends JpaRepository<RevealConsent, RevealConsent.RevealConsentId> {

    /**
     * 특정 매칭·단계의 모든 참여자 동의를 조회한다.
     *
     * @param matchId     매칭 ID
     * @param revealLevel 공개 단계
     * @param deleted     논리 삭제 여부
     * @return 동의 목록
     */
    List<RevealConsent> findByMatchIdAndRevealLevelAndDeleted(Long matchId, Integer revealLevel, String deleted);

    /**
     * 특정 참여자의 동의를 조회한다(삭제 이력 포함).
     *
     * <p>복합키 재사용을 위해 논리 삭제 여부로 거르지 않는다.</p>
     *
     * @param matchId     매칭 ID
     * @param userId      사용자 ID
     * @param revealLevel 공개 단계
     * @return 동의
     */
    Optional<RevealConsent> findByMatchIdAndUserIdAndRevealLevel(Long matchId, Long userId, Integer revealLevel);
}
