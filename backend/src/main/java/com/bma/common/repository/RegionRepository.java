package com.bma.common.repository;

import com.bma.common.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 행정구역 조회.
 */
public interface RegionRepository extends JpaRepository<Region, String> {

    /**
     * 사용 중인 지역을 단계 → 정렬 순서로 모두 조회한다.
     *
     * <p>전체가 246건뿐이라 단계별로 나눠 두 번 조회하는 것보다 한 번에 읽어
     * 애플리케이션에서 묶는 편이 쿼리 수가 적다.</p>
     *
     * @param useYn   사용 여부
     * @param deleted 논리 삭제 여부
     * @return 지역 목록
     */
    List<Region> findByUseYnAndDeletedOrderByLevelAscSortOrderAsc(String useYn, String deleted);

    /**
     * 코드로 사용 중인 지역 한 건을 조회한다.
     *
     * @param code    지역 코드
     * @param useYn   사용 여부
     * @param deleted 논리 삭제 여부
     * @return 지역
     */
    Optional<Region> findByCodeAndUseYnAndDeleted(String code, String useYn, String deleted);
}
