package com.jdec.platform.common_business.biz.auth.Common;

public interface AuthCommonConstant {

    public static final String LOGIN_KEY_PREFIX = "STUDENT:login:current:";

    /** 无需认证的业务端点。 */
    public static final String[] PUBLIC_ENDPOINTS = {
        "/api/student/login",
        "/api/student/permission/check-current-user",
        "/api/student/get-project-info",
        "/api/config"
    };

    /** Swagger / OpenAPI 相关端点。 */
    public static final String[] SWAGGER_ENDPOINTS = {
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
}
