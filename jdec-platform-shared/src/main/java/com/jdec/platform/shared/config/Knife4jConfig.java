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

    /** 接口分组（按 controller 包路径扫描） */
    //    @Bean
    //    public GroupedOpenApi defaultApi() {
    //        return GroupedOpenApi.builder()
    //                .group("default")
    //                .displayName("全部接口")
    //                .packagesToScan("com.jdec.platform") // ← 替换成你的包路径
    //                .build();
    //    }

    // 分组2：配置管理
    @Bean
    public GroupedOpenApi configApi() {
        return GroupedOpenApi.builder()
                .group("CONFIG")
                .displayName("配置管理")
                .packagesToScan("com.jdec.platform.config.biz.controller")
                .build();
    }

    // 分组3：监控日志管理
    @Bean
    public GroupedOpenApi monitorLogApi() {
        return GroupedOpenApi.builder()
                .group("MONITOR_LOG_API")
                .displayName("监控日志管理")
                .packagesToScan("com.jdec.platform.monitorlog.biz.controller")
                .build();
    }

    // 分组4：通用业务管理
    @Bean
    public GroupedOpenApi comBizApi() {
        return GroupedOpenApi.builder()
                .group("comBiz")
                .displayName("通用业务管理")
                .packagesToScan(
                        "com.jdec.platform.common_business.biz.controller",
                        "com.jdec.platform.common_business.biz.auth.controller",
                        "com.jdec.platform.approval.biz.controller")
                .build();
    }

    // 分组5：数据引擎
    @Bean
    public GroupedOpenApi dataApi() {
        return GroupedOpenApi.builder()
                .group("DATA")
                .displayName("数据引擎")
                .packagesToScan("com.jdec.platform.data.biz.controller")
                .build();
    }
}
