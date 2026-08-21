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
     * 클라이언트에 함께 내려줄 오류 상세. 없으면 {@code null}.
     *
     * <p>{@link GlobalExceptionHandler} 가 이 값을 응답 봉투의 {@code data} 에 싣는다.
     * 코드와 메시지만으로 화면을 분기할 수 없는 오류에만 사용한다.</p>
     */
    private final transient Object details;

    /**
     * 기본 메시지로 예외를 생성한다.
     *
     * @param errorCode 업무 오류 코드
     */
    public BusinessException(ErrorCode errorCode) {
        // writableStackTrace=false : 흐름 제어용 예외라 스택 트레이스 수집 비용을 없앤다.
        super(errorCode.getMessage(), null, false, false);
        this.errorCode = errorCode;
        this.details = null;
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
        this.details = null;
    }

    /**
     * 메시지와 상세를 모두 지정해 예외를 생성한다.
     *
     * @param errorCode 업무 오류 코드
     * @param detail    클라이언트에 내려줄 설명
     * @param details   응답 {@code data} 에 실릴 상세 객체
     */
    public BusinessException(ErrorCode errorCode, String detail, Object details) {
        super(detail, null, false, false);
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * 기본 메시지에 구조화된 상세만 덧붙여 예외를 만든다.
     *
     * @param errorCode 업무 오류 코드
     * @param details   응답 {@code data} 에 실릴 상세 객체
     * @return 생성된 예외
     */
    public static BusinessException withDetails(ErrorCode errorCode, Object details) {
        return new BusinessException(errorCode, errorCode.getMessage(), details);
    }
}
