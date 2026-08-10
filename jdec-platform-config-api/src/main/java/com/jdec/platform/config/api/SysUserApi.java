package com.jdec.platform.config.api;

import com.jdec.platform.config.api.bo.SysUserBO;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.CheckUserPermissionByPhoneResp;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.config.api.dto.response.UserSearchTreeNodeResp;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

/** 用户 Service */
public interface SysUserApi {

    /** 分页查询用户列表 */
    PageResult<SysUserPageResp> getUserPage(QuerySysUserPageReq query);

    /** 编辑用户信息 */
    void editUser(UpdateSysUserReq req);

    /** 停用用户 */
    void disableUser(Long id);

    SysUserBO getUserByPhone(String phone);

    /**
     * 根据projectNo查询用户列表
     *
     * <p>先查sys_role_user表根据projectNo过滤拿到用户id，再查sys_user表获取用户数据
     */
    List<SysUserPageResp> getUserList();

    List<UserSearchTreeNodeResp> getUserTreeListForSearch(Integer userType);

    /** 根据角色ID查询用户列表 */
    List<SysUserPageResp> getUsersByRoleId(Long roleId);

    /**
     * 根据用户ID列表批量查询用户
     *
     * <p>根据sys_user表的user_id字段批量查询用户信息
     *
     * @param req 用户ID列表请求
     * @return 用户列表
     */
    List<SysUserPageResp> getUsersByUserIds(QueryUsersByIdsReq req);

    /** 根据用户ID反查关联的主体ID列表 */
    List<Long> getSubjectIdsByUserId(Long userId);

    /**
     * 通过手机号检查用户是否有角色权限
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>先从sys_user表查询用户
     *   <li>如果未查到，调用人力模块UserApi查询
     *   <li>如果人力模块也未查到，返回无权限
     *   <li>如果查到，根据用户的subjectId查询sys_role_user表
     *   <li>在查询时需要加时效性校验（effectiveType、effectiveStartDate、effectiveEndDate）
     * </ol>
     *
     * @param phone 手机号
     * @return 权限检查结果
     */
    CheckUserPermissionByPhoneResp checkUserPermissionByPhone(String phone);

    /**
     * 根据用户ID获取用户详情
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>先从sys_user表根据userId查询用户
     *   <li>如果未查到，调用人力模块UserApi根据userId查询
     *   <li>如果人力模块也未查到，抛出异常
     *   <li>检查权限（系统设置或主体设置）
     *   <li>如果没有权限，抛出异常
     *   <li>如果有权限，返回用户详情
     * </ol>
     *
     * @param userId 用户ID
     * @return 用户详情
     */
    UserDetailResp getUserDetailByUserId(Long userId);
}
