package com.jdec.platform.auth.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录响应，包含认证信息 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResp {
    /** JWT Token */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 账户状态 */
    private Integer status;
}
