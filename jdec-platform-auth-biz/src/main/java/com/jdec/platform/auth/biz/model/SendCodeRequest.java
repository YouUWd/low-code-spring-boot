package com.jdec.platform.auth.biz.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发送验证码请求 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeRequest {
    /** 手机号 */
    private String phone;
}
