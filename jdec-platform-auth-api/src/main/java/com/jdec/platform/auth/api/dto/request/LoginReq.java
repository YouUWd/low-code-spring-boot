package com.jdec.platform.auth.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginReq {
    /** 手机号 */
    private String phone;

    /** 验证码 */
    private String code;

    /** 是否返回员工信息，默认 false */
    private Boolean includeEmployeeInfo = false;
}
