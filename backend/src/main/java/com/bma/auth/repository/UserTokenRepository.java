package com.bma.auth.repository;

import com.bma.auth.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link UserToken} 저장소.
 */
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    /**
     * 해시로 살아 있는 토큰을 찾는다.
     *
     * @param tokenHash 토큰 해시
     * @param tokenType 토큰 종류
     * @param deleted   논리 삭제 여부
     * @return 일치하는 토큰
     */
    Optional<UserToken> findByTokenHashAndTokenTypeAndDeleted(String tokenHash, String tokenType, String deleted);

    /**
     * 특정 사용자의 살아 있는 토큰을 모두 조회한다.
     *
     * <p>토큰 재사용(탈취)이 의심될 때 해당 사용자의 모든 세션을 한 번에 끊는 데 사용한다.</p>
     *
     * @param userId    사용자 ID
     * @param tokenType 토큰 종류
     * @param deleted   논리 삭제 여부
     * @return 토큰 목록
     */
    List<UserToken> findAllByUserIdAndTokenTypeAndDeleted(Long userId, String tokenType, String deleted);

    /**
     * 만료된 토큰 행을 물리 삭제한다.
     *
     * <p>로그인/재발급 때마다 행이 쌓이기만 하면 테이블이 무한정 커진다.
     * 재발급 시점에 해당 사용자의 만료분만 가볍게 정리한다.</p>
     *
     * @param userId 사용자 ID
     * @param now    기준 시각
     * @return 삭제된 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserToken t where t.userId = :userId and t.expireDate < :now")
    int deleteExpiredTokens(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
