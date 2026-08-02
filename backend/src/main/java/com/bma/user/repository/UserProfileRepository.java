package com.bma.user.repository;

import com.bma.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link UserProfile} 저장소.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /**
     * 살아 있는 프로필을 조회한다.
     *
     * @param id      사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 프로필
     */
    Optional<UserProfile> findByIdAndDeleted(Long id, String deleted);

    /**
     * 닉네임 중복 여부를 확인한다.
     *
     * <p>{@code UK_US_USER_PROFILE_NICKNAME} 제약이 있어 사전 확인 없이 저장하면
     * 500(DataIntegrityViolation)으로 터진다.</p>
     *
     * @param nickname 닉네임
     * @param deleted  논리 삭제 여부
     * @return 존재하면 {@code true}
     */
    boolean existsByNicknameAndDeleted(String nickname, String deleted);

    /**
     * 본인을 제외한 닉네임 중복 여부를 확인한다. 프로필 수정 시 사용한다.
     *
     * @param nickname 닉네임
     * @param id       제외할 사용자 ID
     * @param deleted  논리 삭제 여부
     * @return 다른 사용자가 사용 중이면 {@code true}
     */
    boolean existsByNicknameAndIdNotAndDeleted(String nickname, Long id, String deleted);
}
