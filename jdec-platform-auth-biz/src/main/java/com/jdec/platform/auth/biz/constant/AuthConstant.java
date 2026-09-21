package com.jdec.platform.auth.biz.constant;

/** Auth 模块常量（迁移自 student-manage 的 AuthCommonConstant） */
public interface AuthConstant {

    /** 登录上下文在 Redis 中的 key 前缀 */
    String LOGIN_KEY_PREFIX = "STUDENT:login:current:";

    /** Redis 中存放当前有效 token 的 key 前缀，完整 key 为 token:{phone} */
    String TOKEN_KEY_PREFIX = "token:";

    /** Authorization 请求头中的 Bearer 前缀 */
    String BEARER_PREFIX = "Bearer ";

    /** 当前角色缓存在 Redis 中的 key 格式（参数为真实登录用户ID） */
    String USER_CURRENT_ROLE_KEY_PREFIX = "jdec:student:user_current_role:%s";

    /** 模拟登录特殊权限的权限码 */
    String SIMULATE_LOGIN_RIGHT_CODE = "simulate_login";
}
