package com.bma.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 설정.
 *
 * <p>기존에는 Bearer 인증 스킴이 등록되어 있지 않아 Swagger에서 보호된 API를
 * 시험 호출할 수 없었다. Authorize 버튼으로 액세스 토큰을 넣을 수 있도록 스킴을 추가한다.</p>
 */
@Configuration
public class OpenApiConfig {

    /** 보안 스킴 식별자. */
    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * API 문서 메타데이터와 인증 스킴을 정의한다.
     *
     * @return OpenAPI 정의
     */
    @Bean
    public OpenAPI blindMatchingOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인 응답의 accessToken 값을 입력합니다.");

        return new OpenAPI()
                .info(new Info()
                        .title("Blind Matching API")
                        .version("v1")
                        .description("블라인드 소개팅 서비스 백엔드 API"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
