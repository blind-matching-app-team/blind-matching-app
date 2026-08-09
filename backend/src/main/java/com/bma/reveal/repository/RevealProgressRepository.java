package com.bma.reveal.repository;

import com.bma.reveal.entity.RevealProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link RevealProgress} 저장소.
 */
public interface RevealProgressRepository extends JpaRepository<RevealProgress, Long> {

    /**
     * 살아 있는 진행 상태를 조회한다.
     *
     * @param matchId 매칭 ID
     * @param deleted 논리 삭제 여부
     * @return 진행 상태
     */
    Optional<RevealProgress> findByIdAndDeleted(Long matchId, String deleted);

    /**
     * 여러 매칭의 진행 상태를 한 번에 조회한다.
     *
     * <p>매칭 목록 화면에서 매칭마다 개별 조회하면 N+1이 되므로 IN 조건으로 가져온다.</p>
     *
     * @param matchIds 매칭 ID 목록
     * @param deleted  논리 삭제 여부
     * @return 진행 상태 목록
     */
    List<RevealProgress> findByIdInAndDeleted(List<Long> matchIds, String deleted);
}
