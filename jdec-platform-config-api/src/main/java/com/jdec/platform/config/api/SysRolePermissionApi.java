package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SaveRoleDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleMenuReq;
import com.jdec.platform.config.api.dto.request.SaveRoleSpecialPermissionReq;
import com.jdec.platform.config.api.dto.response.RoleDataPermissionResp;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionResp;
import com.jdec.platform.config.api.dto.response.RoleMenuResp;
import com.jdec.platform.config.api.dto.response.RoleSpecialPermissionResp;
import java.util.List;

public interface SysRolePermissionApi {
    /**
     * 根据角色ID查询菜单列表
     *
     * @param roleId 角色ID
     * @param subjectId 主体ID
     * @param appCode 应用编码
     * @return 角色菜单列表
     */
    List<RoleMenuResp> getRoleMenu(Long roleId, Long subjectId, String appCode);

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
}
