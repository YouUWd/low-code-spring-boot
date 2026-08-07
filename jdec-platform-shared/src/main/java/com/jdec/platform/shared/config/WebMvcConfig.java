package com.jdec.platform.shared.config;

import com.jdec.platform.shared.context.interceptor.AppContextInterceptor;
import com.jdec.platform.shared.security.JwtAuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC 配置 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppContextInterceptor appContextInterceptor;
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 认证拦截器（需要先执行）
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        //                                                "/api/**",
                        "/api/config/sys/user/check-permission",
                        "/doc.html", // API 文档
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/favicon.ico");

        // 应用上下文拦截器
        registry.addInterceptor(appContextInterceptor)
                .addPathPatterns("/api/config/**")
                .addPathPatterns("/api/data/**")
                .addPathPatterns("/api/student/**")
                .addPathPatterns("/api/monitorlog/**")
                .excludePathPatterns("/api/config/auth/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // knife4j 静态资源
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
