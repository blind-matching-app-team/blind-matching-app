package com.bma.common.security;

import com.bma.common.exception.ErrorCode;
import com.bma.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청에 대한 401 응답 생성기.
 *
 * <p>기본 구현은 빈 본문이나 HTML 로그인 페이지를 반환해 모바일 클라이언트가 원인을 알 수 없었다.
 * 여기서는 다른 API와 동일한 {@link ApiResponse} 형태로 응답하며,
 * {@link JwtAuthenticationFilter}가 남긴 실패 사유가 있으면 그대로 전달한다.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 필터가 토큰 만료/위조를 구분해 담아 두었으면 그 코드를 쓰고, 없으면 "인증 필요"로 응답한다.
        Object attribute = request.getAttribute(JwtAuthenticationFilter.ATTRIBUTE_ERROR_CODE);
        ErrorCode errorCode = (attribute instanceof ErrorCode code) ? code : ErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}
