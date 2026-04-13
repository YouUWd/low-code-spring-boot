package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.UserRoleEntryResp;
import java.util.List;

/** 用户角色关联服务接口 */
public interface SysUserRoleApi {

    /** 为用户添加角色 */
    UserRoleEntryResp addRoleToUser(Long userId, Long roleId);

    /** 根据用户 ID 查询角色 */
    List<UserRoleEntryResp> getRolesByUserId(Long userId);

    /** 根据角色 ID 查询用户 */
    List<UserRoleEntryResp> getUsersByRoleId(Long roleId);

    /** 检查用户是否拥有角色 */
    boolean hasRole(Long userId, Long roleId);

    /** 从用户移除角色 */
    void removeRoleFromUser(Long userId, Long roleId);

    /** 删除用户的所有角色 */
    void deleteRolesByUserId(Long userId);

    /** 删除角色的所有用户关联 */
    void deleteUsersByRoleId(Long roleId);
}
