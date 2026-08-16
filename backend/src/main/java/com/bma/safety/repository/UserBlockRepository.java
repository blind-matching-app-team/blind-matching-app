package com.bma.safety.repository;

import com.bma.safety.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link UserBlock} 저장소.
 */
public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.UserBlockId> {

    /**
     * 차단 관계를 조회한다(해제된 이력 포함).
     *
     * <p>PK가 (차단자, 대상)이라 해제 후 재차단 시 같은 행을 되살려야 하므로
     * 논리 삭제 여부로 거르지 않는다.</p>
     *
     * @param blockUserId  차단자
     * @param targetUserId 대상
     * @return 차단 정보
     */
    Optional<UserBlock> findByBlockUserIdAndTargetUserId(Long blockUserId, Long targetUserId);

    /**
     * 활성 차단인지 확인한다.
     *
     * @param blockUserId  차단자
     * @param targetUserId 대상
     * @param deleted      논리 삭제 여부
     * @return 차단 중이면 {@code true}
     */
    boolean existsByBlockUserIdAndTargetUserIdAndDeleted(Long blockUserId, Long targetUserId, String deleted);

    /**
     * 내가 차단한 사용자 목록을 조회한다.
     *
     * @param blockUserId 차단자
     * @param deleted     논리 삭제 여부
     * @return 차단 목록
     */
    List<UserBlock> findByBlockUserIdAndDeleted(Long blockUserId, String deleted);

    /**
     * 두 사용자 사이에 어느 방향으로든 차단이 있는지 확인한다.
     *
     * <p>내가 차단했든 상대가 나를 차단했든 매칭과 채팅은 모두 막아야 한다.</p>
     *
     * @param userA 사용자 A
     * @param userB 사용자 B
     * @return 어느 방향으로든 차단이 있으면 {@code true}
     */
    @Query("""
            select count(b) > 0 from UserBlock b
            where b.deleted = 'N'
              and ((b.blockUserId = :userA and b.targetUserId = :userB)
                or (b.blockUserId = :userB and b.targetUserId = :userA))
            """)
    boolean existsBlockBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    /**
     * 나와 차단 관계에 있는 모든 사용자 ID를 조회한다(양방향).
     *
     * <p>추천 목록에서 제외할 대상을 한 번에 가져오기 위한 쿼리다.</p>
     *
     * @param userId 기준 사용자
     * @return 상대 사용자 ID 목록
     */
    @Query("""
            select case when b.blockUserId = :userId then b.targetUserId else b.blockUserId end
            from UserBlock b
            where b.deleted = 'N'
              and (b.blockUserId = :userId or b.targetUserId = :userId)
            """)
    List<Long> findRelatedUserIds(@Param("userId") Long userId);
}
