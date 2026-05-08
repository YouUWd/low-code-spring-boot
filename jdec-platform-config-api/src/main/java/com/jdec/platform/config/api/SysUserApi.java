package com.jdec.platform.config.api;

import com.jdec.platform.config.api.bo.SysUserBO;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.shared.model.PageResult;

import java.util.List;

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

    SysUserBO getUserByPhone(String phone);

    /**
     * 根据appCode查询用户列表
     * <p>先查sys_role_user表根据appCode过滤拿到用户id，再查sys_user表获取用户数据
     */
    List<SysUserPageResp> getUserList();
}
