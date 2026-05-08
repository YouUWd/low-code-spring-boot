package com.jdec.platform.config.api;

import com.jdec.platform.config.api.bo.UserBO;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.shared.model.PageResult;

/**
 * 用户 Service
 */
public interface SysUserApi {

    /**
     * 分页查询用户列表
     */
    PageResult<SysUserPageResp> getUserPage(QuerySysUserPageReq query);

    /**
     * 编辑用户信息
     */
    void editUser(UpdateSysUserReq req);

    /**
     * 停用用户
     */
    void disableUser(Long id);

    UserBO getUserByPhone(String phone);
}
