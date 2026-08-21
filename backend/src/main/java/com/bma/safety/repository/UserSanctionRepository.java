package com.bma.safety.repository;

import com.bma.safety.entity.UserSanction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@link UserSanction} 저장소.
 */
public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    /**
     * 사용자의 활성 제재를 최신순으로 조회한다.
     *
     * <p>여러 건이 겹칠 수 있으므로 목록으로 받아 서비스에서 우선순위를 판단한다.
     * (영구 제재가 있으면 그것이 우선, 없으면 해제가 가장 늦은 것이 우선)</p>
     *
     * @param userId   사용자 ID
     * @param activeYn 활성 여부
     * @param deleted  논리 삭제 여부
     * @return 제재 목록
     */
    List<UserSanction> findByUserIdAndActiveYnAndDeletedOrderByStartDateDesc(Long userId,
                                                                            String activeYn,
                                                                            String deleted);
}
