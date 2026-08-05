package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableFeignClients
@Configuration
public class FeignConfig {

    @Value("${app.project.number}")
    private String projectNumber;

    @Bean
    public RequestInterceptor traceInterceptor() {
        return template -> {
            //            String traceId = UUID.randomUUID().toString();
            //            template.header("X-Trace-Id", traceId);
            template.header("Authorization", "Bearer " + TokenContextHolder.getCurrentUserToken());
            template.header("X-Project-No", projectNumber);
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL; // 日志级别，可以根据需要调整
    }
}
