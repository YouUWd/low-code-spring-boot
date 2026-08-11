package com.jdec.platform.common_business.biz.config;

import com.jdec.platform.common_business.biz.common.RoleCheckInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web配置 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired private RoleCheckInterceptor roleCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 添加用户是否拥有当前角色判断
        registry.addInterceptor(roleCheckInterceptor)
                .addPathPatterns("/api/student/**")
                .excludePathPatterns(
                        "/api/student/login",
                        "/api/student/permission/check-current-user",
                        "/api/student/logout",
                        "/api/student/get-project-info",
                        "/api/student/config/get/role-list",
                        "/api/student/config/get/role/subjects",
                        "/api/student/config/select/role",
                        "/api/student/common/query/get-data-right")
                .order(1);
    }
}
