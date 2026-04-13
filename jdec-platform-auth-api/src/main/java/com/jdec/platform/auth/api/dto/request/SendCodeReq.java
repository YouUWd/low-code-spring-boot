package com.jdec.platform.auth.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发送验证码请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeReq {
    /** 手机号 */
    private String phone;
}
