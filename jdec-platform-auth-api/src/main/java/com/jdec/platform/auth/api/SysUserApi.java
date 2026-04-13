package com.jdec.platform.auth.api;

import com.jdec.platform.auth.api.dto.response.LoginResp;

/** 认证模块暴露的 API 接口 */
public interface SysUserApi {

    /**
     * 发送验证码（需要提供用户的企业微信信息）
     *
     * @param phone 手机号
     * @param subjectId 主体ID
     * @param weChatUserId 企业微信用户ID
     */
    void sendCode(String phone, Integer subjectId, String weChatUserId);

    /**
     * 使用验证码登录
     *
     * @param phone 手机号
     * @param code 验证码
     * @return 登录响应
     */
    LoginResp loginWithCode(String phone, String code);
}
