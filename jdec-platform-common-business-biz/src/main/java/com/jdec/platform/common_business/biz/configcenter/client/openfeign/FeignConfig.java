package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import feign.Logger;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

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

            // 如果是来自 浏览器的请求  把一些必要的http Header 带过去
            // 1. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
            RequestAttributes requestAttributes =
                    org.springframework.web.context.request.RequestContextHolder
                            .getRequestAttributes();
            if ((requestAttributes instanceof ServletRequestAttributes)) {
                // 2.如果在 Web 上下文中
                HttpServletRequest request =
                        ((ServletRequestAttributes) requestAttributes).getRequest();
                // 3. 安全获取并转换 Header 数据 ,origin 前端的服务地址，User-Agent 浏览器的信息
                // request.getHeaderNames()
                template.header("User-Agent", request.getHeader("User-Agent"));
                template.header("Origin", request.getHeader("Origin"));
                template.header("Referer", request.getHeader("Referer"));
                template.header("X-Forwarded-For", LoginUserUtil.getIpFromHeader());
            }
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.HEADERS; // 日志级别，可以根据需要调整
    }
}
