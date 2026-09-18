package com.jdec.platform.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    /** 接口文档基本信息 */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("项目接口文档")
                                .version("v1.0.0")
                                .description("这是接口文档描述")
                                .contact(
                                        new Contact()
                                                .name("作者名")
                                                .email("your@email.com")
                                                .url("https://yoursite.com"))
                                .license(
                                        new License()
                                                .name("Apache 2.0")
                                                .url(
                                                        "https://www.apache.org/licenses/LICENSE-2.0.html")));
    }

    @Bean
    public GroupedOpenApi configApi() {
        return GroupedOpenApi.builder()
                .group("CONFIG")
                .displayName("配置管理")
                .packagesToScan("com.jdec.platform.config.biz.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi dataApi() {
        return GroupedOpenApi.builder()
                .group("DATA")
                .displayName("数据引擎")
                .packagesToScan("com.jdec.platform.data.biz.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi comBizApi() {
        return GroupedOpenApi.builder()
                .group("comBiz")
                .displayName("通用功能管理")
                .packagesToScan("com.jdec.platform.approval.biz.controller")
                .build();
    }
}
