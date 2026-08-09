package com.bma.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 응답 DTO.
 *
 * <p>Spring Data의 {@code PageImpl}을 그대로 직렬화하면 내부 구조가 그대로 노출되고
 * 버전에 따라 JSON 형태가 바뀔 수 있다(Boot 3.3+ 에서 경고를 발생시킨다).
 * API 계약을 고정하기 위해 명시적인 DTO로 변환해서 내려준다.</p>
 *
 * @param <T>           항목 타입
 * @param content       현재 페이지 항목 목록
 * @param page          0부터 시작하는 페이지 번호
 * @param size          페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages    전체 페이지 수
 * @param last          마지막 페이지 여부
 */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages,
                              boolean last) {

    /**
     * {@link Page}의 각 항목을 변환하면서 페이지 응답을 만든다.
     *
     * @param page   원본 페이지
     * @param mapper 엔티티 → DTO 변환 함수
     * @param <E>    원본 항목 타입
     * @param <T>    변환 후 항목 타입
     * @return 페이지 응답
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
