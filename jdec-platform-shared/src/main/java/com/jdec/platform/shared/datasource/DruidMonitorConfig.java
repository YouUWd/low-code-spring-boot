package com.jdec.platform.shared.datasource;

import com.alibaba.druid.support.jakarta.StatViewServlet;
import com.alibaba.druid.support.jakarta.WebStatFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Druid 监控配置。
 *
 * <p>配置 Druid 监控页面和 Web 统计过滤器。
 *
 * <p>访问地址：http://localhost:8080/druid
 */
@Configuration
public class DruidMonitorConfig {

    /**
     * 配置 Druid 监控页面。
     *
     * @return Servlet 注册 Bean
     */
    @Bean
    public ServletRegistrationBean<StatViewServlet> druidStatViewServlet() {
        ServletRegistrationBean<StatViewServlet> registrationBean =
                new ServletRegistrationBean<>(new StatViewServlet(), "/druid/*");

        // 监控页面登录账号密码（开发环境可以设置简单密码，生产环境务必修改）
        registrationBean.addInitParameter("loginUsername", "admin");
        registrationBean.addInitParameter("loginPassword", "admin123");

        // IP 白名单（为空表示允许所有访问，开发环境可以不限制）
        registrationBean.addInitParameter("allow", "");

        // IP 黑名单（优先级高于白名单）
        // registrationBean.addInitParameter("deny", "192.168.1.100");

        // 是否允许清空统计数据
        registrationBean.addInitParameter("resetEnable", "true");

        return registrationBean;
    }

    /**
     * 配置 Web 统计过滤器。
     *
     * @return Filter 注册 Bean
     */
    @Bean
    public FilterRegistrationBean<WebStatFilter> druidWebStatFilter() {
        FilterRegistrationBean<WebStatFilter> registrationBean =
                new FilterRegistrationBean<>(new WebStatFilter());

        // 拦截所有请求
        registrationBean.addUrlPatterns("/*");

        // 排除静态资源和监控页面
        registrationBean.addInitParameter(
                "exclusions", "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*");

        // 是否记录 Session 统计
        registrationBean.addInitParameter("sessionStatEnable", "true");

        // Session 统计的最大数量
        registrationBean.addInitParameter("sessionStatMaxCount", "1000");

        // 配置 principalSessionName，使得 Druid 能够知道当前的用户是谁
        // 如果你的应用使用了 Spring Security 或其他安全框架，可以配置这个参数
        // registrationBean.addInitParameter("principalSessionName", "user");

        return registrationBean;
    }
}
