package com.bma.user.repository;

import com.bma.user.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link UserPreference} 저장소.
 */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /**
     * 살아 있는 선호 조건을 조회한다.
     *
     * @param id      사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 선호 조건
     */
    Optional<UserPreference> findByIdAndDeleted(Long id, String deleted);
}
