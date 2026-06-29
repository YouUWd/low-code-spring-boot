package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SaveRoleDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleMenuReq;
import com.jdec.platform.config.api.dto.request.SaveRoleModuleFieldPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleSpecialPermissionReq;
import com.jdec.platform.config.api.dto.response.RoleDataPermissionResp;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionResp;
import com.jdec.platform.config.api.dto.response.RoleMenuResp;
import com.jdec.platform.config.api.dto.response.RoleModuleFieldPermissionResp;
import com.jdec.platform.config.api.dto.response.RoleSpecialPermissionResp;
import java.util.List;

public interface SysRolePermissionApi {
    /**
     * 根据角色ID查询菜单列表
     *
     * @param roleId 角色ID
     * @param subjectId 主体ID
     * @param projectNo 项目编号
     * @return 角色菜单列表
     */
    List<RoleMenuResp> getRoleMenu(Long roleId, Long subjectId, String projectNo);

    /**
     * 根据角色ID和菜单分类查询菜单列表
     *
     * @param roleId 角色ID
     * @param category 菜单分类(1:业务菜单 2:系统菜单)
     * @param subjectId 主体ID
     * @param projectNo 项目编号
     * @return 角色菜单列表
     */
    List<RoleMenuResp> getRoleMenuByCategory(
            Long roleId, Integer category, Long subjectId, String projectNo);

    /**
     * 保存角色菜单关联
     *
     * @param request 保存请求
     */
    void saveRoleMenu(SaveRoleMenuReq request);

    /**
     * 根据角色ID查询交互权限列表
     *
     * @param roleId 角色ID
     * @return 角色交互权限列表
     */
    List<RoleInteractionPermissionResp> getRoleInteractionPermission(Long roleId);

    /**
     * 保存角色交互权限关联
     *
     * @param request 保存请求
     */
    void saveRoleInteractionPermission(SaveRoleInteractionPermissionReq request);

    /**
     * 保存角色数据权限关联
     *
     * @param request 保存请求
     */
    void saveRoleDataPermission(SaveRoleDataPermissionReq request);

    /**
     * 根据角色ID查询数据权限列表
     *
     * @param roleId 角色ID
     * @return 角色数据权限列表
     */
    List<RoleDataPermissionResp> getRoleDataPermission(Long roleId);

    /**
     * 根据角色ID查询特殊权限列表
     *
     * @param roleId 角色ID
     * @return 角色特殊权限列表
     */
    List<RoleSpecialPermissionResp> getRoleSpecialPermission(Long roleId);

    /**
     * 保存角色特殊权限关联
     *
     * @param request 保存请求
     */
    void saveRoleSpecialPermission(SaveRoleSpecialPermissionReq request);

    /**
     * 保存角色模块字段权限
     *
     * @param request 保存请求
     */
    void saveRoleModuleFieldPermission(SaveRoleModuleFieldPermissionReq request);

    /**
     * 根据角色ID查询模块字段权限列表
     *
     * @param roleId 角色ID
     * @return 角色模块字段权限列表
     */
    List<RoleModuleFieldPermissionResp> getRoleModuleFieldPermission(Long roleId);

    /**
     * 检查用户是否有模块字段权限
     *
     * @param userId 用户ID
     * @param subjectId 主体ID
     * @param projectNo 项目编号
     * @return 是否有权限
     */
    boolean checkUserHasPermission(Long userId, Long subjectId, String projectNo);
}
