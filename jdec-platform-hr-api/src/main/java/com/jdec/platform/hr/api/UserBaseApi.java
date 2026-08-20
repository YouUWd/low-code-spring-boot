package com.jdec.platform.hr.api;

import com.jdec.platform.hr.api.dto.request.CreateUserBaseReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserBaseReq;
import com.jdec.platform.hr.api.dto.response.UserBaseResp;
import com.jdec.platform.shared.model.PageResult;
import java.util.Optional;

/** 用户基本信息模块暴露的 API */
public interface UserBaseApi {

    /**
     * 根据用户ID获取用户基本信息
     *
     * @param userId 用户ID
     * @return 用户基本信息
     */
    Optional<UserBaseResp> getUserBaseById(Integer userId);

    /**
     * 分页查询用户基本信息
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<UserBaseResp> getUserBaseByPage(Long pageNum, Long pageSize);

    /**
     * 创建用户基本信息
     *
     * @param request 创建用户基本信息请求
     * @return 用户基本信息
     */
    UserBaseResp createUserBase(CreateUserBaseReq request);

    /**
     * 更新用户基本信息
     *
     * @param request 更新用户基本信息请求
     * @return 用户基本信息
     */
    UserBaseResp updateUserBase(UpdateUserBaseReq request);

    /**
     * 删除用户基本信息
     *
     * @param userId 用户ID
     */
    void deleteUserBase(Integer userId);
}
