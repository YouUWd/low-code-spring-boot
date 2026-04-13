package com.jdec.platform.auth.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统一的登录响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedLoginResp {
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

    /** 员工信息（可选） */
    private EmployeeInfoResp employeeInfo;
}
