package com.jdec.platform.hr.api;

import com.jdec.platform.hr.api.dto.request.CreateUserReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserReq;
import com.jdec.platform.hr.api.dto.response.UserBasicInfoResp;
import java.util.Optional;

/** 用户模块暴露的 API */
public interface UserApi {

    /**
     * 根据手机号获取用户的主体ID
     *
     * @param phone 手机号
     * @return 主体ID
     */
    Optional<Integer> getSubjectIdByPhone(String phone);

    /**
     * 根据手机号获取用户的企业微信用户ID（short_name）
     *
     * @param phone 手机号
     * @return 企业微信用户ID
     */
    Optional<String> getWeChatUserIdByPhone(String phone);

    /**
     * 根据手机号获取用户基本信息
     *
     * @param phone 手机号
     * @return 用户基本信息
     */
    Optional<UserBasicInfoResp> getUserBasicInfoByPhone(String phone);

    /**
     * 创建用户
     *
     * @param request 创建用户请求
     * @return 用户基本信息
     */
    UserBasicInfoResp createUser(CreateUserReq request);

    /**
     * 更新用户
     *
     * @param request 更新用户请求
     * @return 用户基本信息
     */
    UserBasicInfoResp updateUser(UpdateUserReq request);
}
