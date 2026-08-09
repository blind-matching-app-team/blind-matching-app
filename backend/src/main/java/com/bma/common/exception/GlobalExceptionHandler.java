package com.bma.common.exception;

import com.bma.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li>기존에는 모든 예외를 로그 없이 500으로 삼켜서 장애 원인 추적이 불가능했다. 이제 전부 로깅한다.</li>
 *   <li>잘못된 JSON, 타입 불일치, 유니크 제약 위반 등 클라이언트 잘못인 경우를 4xx로 분리했다.</li>
 *   <li>500 응답에는 내부 메시지를 노출하지 않고 추적용 로그만 남긴다.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 업무 규칙 위반. 예상된 실패이므로 WARN 레벨로만 남긴다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[업무 오류] {} {} - {}: {}", request.getMethod(), request.getRequestURI(),
                errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    /**
     * {@code @Valid} 본문 검증 실패. 어느 필드가 왜 틀렸는지 모두 알려준다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[검증 실패] {}", detail);
        return badRequest(ErrorCode.INVALID_REQUEST, detail.isEmpty() ? null : detail);
    }

    /**
     * {@code @Validated} 파라미터 검증 실패.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("[파라미터 검증 실패] {}", detail);
        return badRequest(ErrorCode.INVALID_REQUEST, detail.isEmpty() ? null : detail);
    }

    /**
     * 본문 JSON 파싱 실패, 필수 파라미터 누락, 타입 불일치.
     *
     * <p>기존에는 전부 500으로 나갔지만 명백히 클라이언트 잘못이므로 400으로 내린다.</p>
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception e) {
        log.warn("[잘못된 요청 형식] {}", e.getMessage());
        return badRequest(ErrorCode.MALFORMED_REQUEST, null);
    }

    /**
     * 업로드 용량 초과.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("[업로드 용량 초과] {}", e.getMessage());
        return badRequest(ErrorCode.INVALID_FILE, "허용된 파일 크기를 초과했습니다.");
    }

    /**
     * 유니크/외래키 제약 위반.
     *
     * <p>동시 요청으로 인한 중복 삽입 등에서 발생한다. 409로 내려 클라이언트가 재시도 여부를 판단하게 한다.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("[데이터 정합성 위반] {}", e.getMostSpecificCause().getMessage());
        ErrorCode errorCode = ErrorCode.DATA_INTEGRITY_VIOLATION;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 서비스 계층에서 흘러나온 {@code Optional.orElseThrow()} 기본 예외.
     *
     * <p>원래는 각 서비스가 {@link BusinessException}을 던져야 하지만,
     * 누락된 경우에도 500 대신 404로 응답하도록 안전망을 둔다.</p>
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSuchElement(NoSuchElementException e) {
        log.warn("[리소스 없음] {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 메서드 미지원, 핸들러 없음.
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        log.warn("[경로 없음] {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 메서드 보안({@code @PreAuthorize}) 등에서 발생한 권한 부족.
     *
     * <p>주의: 필터 체인에서 발생한 AccessDeniedException은 이 핸들러가 아니라
     * {@link com.bma.common.security.JwtAccessDeniedHandler}가 처리한다.</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("[권한 부족] {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 그 밖의 모든 예외.
     *
     * <p>스택 트레이스를 반드시 남기되, 응답 본문에는 내부 정보를 담지 않는다.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("[미처리 예외] {} {}", request.getMethod(), request.getRequestURI(), e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 400 응답 생성 헬퍼.
     *
     * @param errorCode 오류 코드
     * @param detail    구체적인 설명. {@code null}이면 코드의 기본 메시지를 사용한다.
     * @return 400 응답
     */
    private ResponseEntity<ApiResponse<Void>> badRequest(ErrorCode errorCode, String detail) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), detail != null ? detail : errorCode.getMessage()));
    }
}
