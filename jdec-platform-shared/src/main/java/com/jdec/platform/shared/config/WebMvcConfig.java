package com.jdec.platform.shared.config;

import com.jdec.platform.shared.context.interceptor.SubjectInterceptor;
import com.jdec.platform.shared.datasource.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final SubjectInterceptor subjectInterceptor;

    public WebMvcConfig(TenantInterceptor tenantInterceptor, SubjectInterceptor subjectInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
        this.subjectInterceptor = subjectInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/config/**")
                .excludePathPatterns("/api/config/auth/**"); // 登录接口不需要租户信息

        registry.addInterceptor(subjectInterceptor)
                .addPathPatterns("/api/config/**")
                .excludePathPatterns("/api/config/auth/**"); // 登录接口不需要租户信息
    }
}
