package com.jdec.platform.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI (Swagger) 全局配置类 负责定义文档基本信息、全鉴权方案以及 API 分组。 */
@Configuration
public class OpenApiConfig {

    /** 服务于 OpenAPI 的模型解析器。 强行让 Swagger 使用与 Spring 容器相同的 ObjectMapper（从而继承 SNAKE_CASE 等全局配置）。 */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(
                        new Info()
                                .title("JDEC Platform API")
                                .version("1.0.0")
                                .description(
                                        "JDEC 企业级模块化平台接口文档 — 基于 Spring Boot 3.5 & Spring Modulith"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }

    /** HR 模块接口分组 */
    @Bean
    public GroupedOpenApi hrApi() {
        return GroupedOpenApi.builder().group("1. HR 模块").pathsToMatch("/api/hr/**").build();
    }

    /** Auth 认证模块接口分组 */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder().group("2. Auth 认证模块").pathsToMatch("/api/auth/**").build();
    }

    /** Config 配置中心模块接口分组 */
    @Bean
    public GroupedOpenApi configApi() {
        return GroupedOpenApi.builder()
                .group("3. Config 配置中心")
                .pathsToMatch("/api/config/**", "/api/metadata/**", "/api/data/**")
                .build();
    }

    /** 全部接口分组 */
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder().group("0. 全部接口").pathsToMatch("/api/**").build();
    }
}
