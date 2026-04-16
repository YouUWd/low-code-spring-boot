package com.jdec.platform.auth.api;

import com.jdec.platform.auth.api.dto.response.LoginResp;
import com.jdec.platform.auth.api.dto.response.UserEntryResp;
import com.jdec.platform.shared.model.PageResult;

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

    /**
     * 分页获取用户列表
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页用户信息
     */
    PageResult<UserEntryResp> listUsers(Long pageNum, Long pageSize);
}
