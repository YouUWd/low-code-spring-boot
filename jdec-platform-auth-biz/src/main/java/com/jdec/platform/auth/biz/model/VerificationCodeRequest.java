package com.jdec.platform.auth.biz.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 验证码登陆请求 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationCodeRequest {
    /** 手机号 */
    private String phone;

    /** 验证码 */
    private String code;
}
