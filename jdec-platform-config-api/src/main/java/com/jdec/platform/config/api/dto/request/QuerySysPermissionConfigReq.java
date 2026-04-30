package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/**
 * 查询权限配置请求 DTO
 */
@Data
public class QuerySysPermissionConfigReq {

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 表名（模糊查询）
     */
    private String tableName;

    /**
     * 字段名称（模糊查询）
     */
    private String fieldName;

    /**
     * 模块 ID
     */
    private Long moduleId;

    /**
     * 权限状态: active-启用, inactive-禁用
     */
    private String permissionStatus;
}
