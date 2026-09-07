package com.bma.common.controller;

import com.bma.common.dto.RegionDtos.SidoView;
import com.bma.common.response.ApiResponse;
import com.bma.common.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 행정구역 조회 API.
 *
 * <p>S3 프로필 설정의 지역 선택창(S3-10)이 쓰는 목록이다.</p>
 */
@Tag(name = "Region", description = "행정구역 조회")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    /**
     * 시/도와 하위 시/군/구를 트리로 조회한다.
     *
     * @return 시/도 목록
     */
    @Operation(summary = "행정구역 목록 조회", description = "시/도와 그 하위 시/군/구를 트리로 반환한다.")
    @GetMapping
    public ApiResponse<List<SidoView>> regions() {
        return ApiResponse.ok(regionService.findTree());
    }
}
