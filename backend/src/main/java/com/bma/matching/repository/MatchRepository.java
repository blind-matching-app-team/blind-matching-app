package com.bma.matching.repository;

import com.bma.matching.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link Match} 저장소.
 */
public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * 사용자가 참여 중인 매칭을 조회한다.
     *
     * <p>기존의 {@code findByUserId1OrUserId2}는 상태·논리삭제 조건이 없어
     * 해제된 매칭까지 함께 반환했다.</p>
     *
     * @param userId 사용자 ID
     * @param status 매칭 상태
     * @return 매칭 목록(최신순)
     */
    @Query("""
            select m from Match m
            where m.deleted = 'N' and m.matchStatus = :status
              and (m.user1Id = :userId or m.user2Id = :userId)
            order by m.matchDate desc
            """)
    List<Match> findActiveMatches(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 두 사용자 사이의 매칭을 조회한다.
     *
     * <p>{@code UK_MT_MATCH_USERS}가 (작은 ID, 큰 ID) 기준이므로 호출 전에 정렬된 값을 넘겨야 한다.</p>
     *
     * @param user1Id 작은 사용자 ID
     * @param user2Id 큰 사용자 ID
     * @return 매칭
     */
    Optional<Match> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    /**
     * 사용자가 참여한 특정 매칭을 조회한다.
     *
     * <p>매칭 ID만으로 조회한 뒤 참여자를 검사하면 존재 여부가 노출되므로
     * 참여자 조건을 쿼리에 포함한다.</p>
     *
     * @param matchId 매칭 ID
     * @param userId  사용자 ID
     * @return 매칭
     */
    @Query("""
            select m from Match m
            where m.id = :matchId and m.deleted = 'N'
              and (m.user1Id = :userId or m.user2Id = :userId)
            """)
    Optional<Match> findByIdAndParticipant(@Param("matchId") Long matchId, @Param("userId") Long userId);

    /**
     * 사용자와 매칭된 상대들의 ID를 조회한다. 추천 목록에서 제외하는 데 사용한다.
     *
     * @param userId 사용자 ID
     * @return 상대 사용자 ID 목록
     */
    @Query("""
            select case when m.user1Id = :userId then m.user2Id else m.user1Id end
            from Match m
            where m.deleted = 'N' and (m.user1Id = :userId or m.user2Id = :userId)
            """)
    List<Long> findPartnerIds(@Param("userId") Long userId);
}
