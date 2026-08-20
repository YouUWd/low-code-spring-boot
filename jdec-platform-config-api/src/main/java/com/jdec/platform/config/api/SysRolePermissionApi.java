package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.*;
import com.jdec.platform.config.api.dto.response.*;
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
     * 根据角色ID和模块ID查询交互权限列表，按类型分组返回
     *
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @return 按类型分组的角色交互权限列表
     */
    RoleInteractionPermissionByModuleResp getRoleInteractionPermissionByModule(
            Long roleId, Long moduleId);

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
    RoleDataPermissionResp getRoleDataPermission(Long roleId);

    /**
     * 根据角色ID查询数据权限详情（包含业务数据列表）
     *
     * @param roleId 角色ID
     * @return 角色数据权限详情列表
     */
    List<DataRightConfigResp> getRoleDataPermissionDetail(Long roleId);

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
     * 根据角色ID和模块类别查询模块字段权限列表
     *
     * @param roleId 角色ID
     * @param category 模块类别 (1=业务模块, 2=系统模块)
     * @return 角色模块字段权限列表
     */
    List<RoleModuleFieldPermissionResp> getRoleModuleFieldPermission(Long roleId, Integer category);

    /**
     * 检查角色是否有模块字段权限
     *
     * @param roleId 角色ID
     * @param subjectId 主体ID
     * @param projectNo 项目编号
     * @return 是否有权限
     */
    boolean checkRoleHasPermission(Long roleId, Long subjectId, String projectNo);

    /**
     * 根据角色ID查询用户个性化设置（仅包含列表类型模块）
     *
     * @param roleId 角色ID
     * @param moduleId 模块ID（可选，不传则查询所有模块）
     * @return 用户个性化设置列表
     */
    List<PersonalSettingResp> getPersonalSettingByRole(Long roleId, Long moduleId);

    /**
     * 检查角色是否有模块写入权限
     *
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @return 是否有权限
     */
    boolean checkRoleHasModuleWrite(Long roleId, Long moduleId);
}
