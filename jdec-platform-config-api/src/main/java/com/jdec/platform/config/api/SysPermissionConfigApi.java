package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.CreateSysPermissionConfigReq;
import com.jdec.platform.config.api.dto.request.QuerySysPermissionConfigReq;
import com.jdec.platform.config.api.dto.request.UpdateSysPermissionConfigReq;
import com.jdec.platform.config.api.dto.response.SysPermissionConfigDetailResp;
import com.jdec.platform.config.api.dto.response.SysPermissionConfigEntryResp;

import java.util.List;

/**
 * 权限配置管理 API 接口
 * 定义权限配置的 CRUD 和查询操作
 * 权限模型：位运算（READ:1, CREATE:2, UPDATE:4）
 */
public interface SysPermissionConfigApi {

    /**
     * 创建权限配置
     *
     * @param request 创建请求
     * @return 权限配置条目响应
     */
    SysPermissionConfigEntryResp createPermissionConfig(CreateSysPermissionConfigReq request);

    /**
     * 根据 ID 获取权限配置详情
     *
     * @param permissionId 权限配置 ID
     * @return 权限配置详情响应
     */
    SysPermissionConfigDetailResp getPermissionConfigById(Long permissionId);

    /**
     * 获取字段的权限配置
     *
     * @param tenantId 租户 ID
     * @param tableName 表名
     * @param fieldName 字段名称
     * @return 权限配置条目响应
     */
    SysPermissionConfigEntryResp getPermissionByField(Long tenantId, String tableName, String fieldName);

    /**
     * 查询权限配置
     *
     * @param request 查询请求
     * @return 权限配置条目响应列表
     */
    List<SysPermissionConfigEntryResp> queryPermissionConfigs(QuerySysPermissionConfigReq request);

    /**
     * 更新权限配置
     *
     * @param request 更新请求
     * @return 权限配置条目响应
     */
    SysPermissionConfigEntryResp updatePermissionConfig(UpdateSysPermissionConfigReq request);

    /**
     * 删除权限配置
     *
     * @param permissionId 权限配置 ID
     */
    void deletePermissionConfig(Long permissionId);

    /**
     * 批量删除权限配置
     *
     * @param permissionIds 权限配置 ID 列表
     */
    void batchDeletePermissionConfigs(List<Long> permissionIds);

    /**
     * 检查是否有指定权限
     *
     * @param tenantId 租户 ID
     * @param tableName 表名
     * @param fieldName 字段名称
     * @param operation 操作类型（1-READ, 2-CREATE, 4-UPDATE）
     * @return 是否有权限
     */
    boolean hasPermission(Long tenantId, String tableName, String fieldName, Integer operation);
}
