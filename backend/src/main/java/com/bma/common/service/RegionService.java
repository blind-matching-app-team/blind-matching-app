package com.bma.common.service;

import com.bma.common.dto.RegionDtos.SidoView;
import com.bma.common.dto.RegionDtos.SigunguView;
import com.bma.common.entity.Region;
import com.bma.common.entity.YesNo;
import com.bma.common.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 행정구역 조회.
 *
 * <p>프로필 저장 시 지역 코드 검증에도 쓰인다. 검증 없이 문자열을 그대로 저장하면
 * 오타나 임의 값이 들어와 매칭 지역 필터가 조용히 어긋난다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    /**
     * 시/도와 하위 시/군/구를 트리로 조회한다.
     *
     * @return 시/도 목록. 각 시/도는 하위 시/군/구를 함께 담는다
     */
    public List<SidoView> findTree() {
        List<Region> all = regionRepository.findByUseYnAndDeletedOrderByLevelAscSortOrderAsc(YesNo.Y, YesNo.N);

        Map<String, List<SigunguView>> childrenByParent = all.stream()
                .filter(Region::isSigungu)
                .collect(Collectors.groupingBy(
                        Region::getParentCode,
                        // 조회 쿼리가 SORT_ORDER 로 정렬해 오므로 그 순서를 유지한다.
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(SigunguView::from, Collectors.toList())));

        return all.stream()
                .filter(Region::isSido)
                .map(sido -> SidoView.of(sido, childrenByParent.getOrDefault(sido.getCode(), List.of())))
                .toList();
    }

    /**
     * 프로필에 저장할 수 있는 지역인지 확인한다.
     *
     * <p>시/도만 고른 상태는 통과시키지 않는다. 사양서상 지역 선택은
     * 시/도 → 시/군/구 2단계를 모두 거치도록 돼 있다.</p>
     *
     * @param code 지역 코드
     * @return 사용 중인 시/군/구면 해당 지역, 아니면 비어 있음
     */
    public Optional<Region> findSelectableSigungu(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return regionRepository.findByCodeAndUseYnAndDeleted(code, YesNo.Y, YesNo.N)
                .filter(Region::isSigungu);
    }

    /**
     * 매칭 선호조건(S4-04)에 저장할 수 있는 지역인지 확인한다.
     *
     * <p>프로필과 달리 시/도도 통과시킨다. 사양서 S4-04 가 "서울 전체"처럼
     * 시/도 단위 전체 선택을 허용하기 때문이다.</p>
     *
     * @param code 지역 코드
     * @return 사용 중인 시/도 또는 시/군/구면 해당 지역, 아니면 비어 있음
     */
    public Optional<Region> findSelectable(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return regionRepository.findByCodeAndUseYnAndDeleted(code, YesNo.Y, YesNo.N)
                .filter(region -> region.isSido() || region.isSigungu());
    }

    /**
     * 지역 코드로 상위 시/도를 찾는다.
     *
     * @param sigungu 시/군/구
     * @return 상위 시/도. 데이터가 어긋나 상위가 없으면 비어 있음
     */
    public Optional<Region> findParent(Region sigungu) {
        if (sigungu == null || sigungu.getParentCode() == null) {
            return Optional.empty();
        }
        return regionRepository.findByCodeAndUseYnAndDeleted(sigungu.getParentCode(), YesNo.Y, YesNo.N);
    }

    /**
     * 지역이 속한 시/도를 찾는다. 시/도 자신이면 그대로 돌려준다.
     *
     * @param region 시/도 또는 시/군/구
     * @return 시/도. 없으면 비어 있음
     */
    public Optional<Region> findSidoOf(Region region) {
        if (region == null) {
            return Optional.empty();
        }
        return region.isSido() ? Optional.of(region) : findParent(region);
    }
}
