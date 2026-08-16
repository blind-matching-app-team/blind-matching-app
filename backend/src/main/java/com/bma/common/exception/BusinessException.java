package com.bma.common.exception;

import lombok.Getter;

/**
 * 업무 규칙 위반을 나타내는 예외.
 *
 * <p>{@link GlobalExceptionHandler}가 이 예외를 잡아 {@link ErrorCode}에 정의된
 * HTTP 상태와 코드로 변환한다. 스택 트레이스가 필요 없는 "예상된 실패"이므로
 * 성능을 위해 스택 트레이스 수집을 생략한다.</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 이 예외가 나타내는 업무 오류 코드. */
    private final transient ErrorCode errorCode;

    /**
     * 기본 메시지로 예외를 생성한다.
     *
     * @param errorCode 업무 오류 코드
     */
    public BusinessException(ErrorCode errorCode) {
        // writableStackTrace=false : 흐름 제어용 예외라 스택 트레이스 수집 비용을 없앤다.
        super(errorCode.getMessage(), null, false, false);
        this.errorCode = errorCode;
    }

    /**
     * 상황을 덧붙인 메시지로 예외를 생성한다.
     *
     * @param errorCode 업무 오류 코드
     * @param detail    클라이언트에 내려줄 구체적인 설명
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail, null, false, false);
        this.errorCode = errorCode;
    }
}
