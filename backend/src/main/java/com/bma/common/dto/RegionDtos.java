package com.bma.common.dto;

import com.bma.common.entity.Region;

import java.util.List;

/**
 * 행정구역 응답 DTO 모음.
 */
public final class RegionDtos {

    private RegionDtos() {
    }

    /**
     * 시/군/구.
     *
     * @param code 지역 코드
     * @param name 지역명
     */
    public record SigunguView(String code, String name) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param region 지역 엔티티
         * @return 응답 DTO
         */
        public static SigunguView from(Region region) {
            return new SigunguView(region.getCode(), region.getName());
        }
    }

    /**
     * 시/도와 그 하위 시/군/구.
     *
     * <p>화면이 시/도 → 시/군/구 2단계 드롭다운이라 트리로 한 번에 내려준다.
     * 전체가 246건이라 2차 요청을 왕복시킬 이유가 없다.</p>
     *
     * @param code     시/도 코드
     * @param name     시/도명
     * @param children 하위 시/군/구
     */
    public record SidoView(String code, String name, List<SigunguView> children) {

        /**
         * 엔티티와 하위 목록을 묶어 응답 DTO를 만든다.
         *
         * @param region   시/도 엔티티
         * @param children 하위 시/군/구
         * @return 응답 DTO
         */
        public static SidoView of(Region region, List<SigunguView> children) {
            return new SidoView(region.getCode(), region.getName(), children);
        }
    }
}
