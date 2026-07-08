package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.config.api.dto.response.SysModuleFieldTreeResp;
import java.util.List;

/** 模块字段权限管理 API 接口 */
public interface SysModuleFieldPermissionApi {

    /**
     * 获取纯净的模块-表-字段树形结构
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param category 模块类别 (1=业务模块, 2=系统模块)
     * @return 模块表字段树列表
     */
    List<SysModuleFieldTreeResp> getModuleTableFieldTree(
            String projectNo, Long subjectId, Integer category);

    /**
     * 按表和权限类型分组查询角色模块字段权限集合
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @return 包含表维度的可读、可写、可更新分组字段集合的响应
     */
    List<PermissionTableGroupResp> getRoleModuleFieldPermissions(
            String projectNo, Long subjectId, Long roleId, Long moduleId);
}
