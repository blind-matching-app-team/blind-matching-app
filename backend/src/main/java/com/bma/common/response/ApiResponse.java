package com.bma.common.response;

import java.time.LocalDateTime;

/**
 * 모든 REST 응답을 감싸는 공통 봉투(envelope).
 *
 * @param <T>       실제 데이터 타입
 * @param success   처리 성공 여부
 * @param code      성공 시 "SUCCESS", 실패 시 {@link com.bma.common.exception.ErrorCode}의 코드
 * @param message   사용자에게 보여줄 수 있는 메시지
 * @param data      응답 본문. 실패 시 {@code null}
 * @param timestamp 응답 생성 시각
 */
public record ApiResponse<T>(boolean success, String code, String message, T data, LocalDateTime timestamp) {

    /** 성공 응답의 고정 코드. */
    private static final String SUCCESS_CODE = "SUCCESS";

    /** 성공 응답의 기본 메시지. */
    private static final String SUCCESS_MESSAGE = "성공";

    /**
     * 데이터를 담은 성공 응답을 만든다.
     *
     * @param data 응답 본문
     * @param <T>  본문 타입
     * @return 성공 응답
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data, LocalDateTime.now());
    }

    /**
     * 본문 없는 성공 응답을 만든다.
     *
     * @return 성공 응답
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, null, LocalDateTime.now());
    }

    /**
     * 실패 응답을 만든다.
     *
     * @param code    오류 코드
     * @param message 오류 메시지
     * @return 실패 응답
     */
    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, LocalDateTime.now());
    }

    /**
     * 상세 데이터를 담은 실패 응답을 만든다.
     *
     * <p>정지 계정 안내처럼 클라이언트가 화면을 분기하려면 코드와 메시지만으로는 부족한 경우가 있다.
     * 그런 오류는 {@code data} 에 구조화된 정보를 함께 내려준다.</p>
     *
     * @param code    오류 코드
     * @param message 오류 메시지
     * @param data    오류 상세. 필요 없으면 {@link #error(String, String)} 를 쓴다
     * @param <T>     상세 타입
     * @return 실패 응답
     */
    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data, LocalDateTime.now());
    }
}
