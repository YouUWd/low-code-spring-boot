package com.jdec.platform.auth.api;

import com.jdec.platform.auth.api.dto.request.LoginReq;
import com.jdec.platform.auth.api.dto.request.SendCodeReq;
import com.jdec.platform.auth.api.dto.response.LoginResp;

/** 认证 Service */
public interface AuthApi {

    /** 用户名密码登录 */
    LoginResp login(LoginReq request);

    /** 退出登录 */
    void logout();

    /** 获取当前用户信息 */
    LoginResp getUserInfo();

    String sendVerifyCode(SendCodeReq req);
}
