package com.jdec.platform.common_business.biz.auth.service;

import com.jdec.platform.common_business.biz.auth.dto.resp.CurrentUserCheckResp;
import com.jdec.platform.common_business.biz.auth.dto.resp.LoginResult;

/** Auth 模块暴露的 API */
public interface AuthApi {

    LoginResult login(String token);

    CurrentUserCheckResp getCurrentUserCheckInfo(String phone);

    // 从 Redis 中获取检验 用户的 token 是否有效
    public Boolean checkUserToken(String token, String phone);

    // 从 Redis 中删除 用户的 token
    public Boolean removeUserToken(String token, String phone);

    // 从 Redis 中删除 用户的 loginContext
    public Boolean removeUserLogin(String token, String phone);

    Boolean logout(String token);

    // 抓取token
    public String extractToken(String headString);
}
