package com.jdec.platform.shared.config;

import com.jdec.platform.shared.context.interceptor.AppContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC 配置 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppContextInterceptor appContextInterceptor;

    public WebMvcConfig(AppContextInterceptor appContextInterceptor) {
        this.appContextInterceptor = appContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(appContextInterceptor)
                .addPathPatterns("/api/config/**")
                .addPathPatterns("/api/data/**")
                .excludePathPatterns("/api/config/auth/**"); // 登录接口不需要租户信息
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
