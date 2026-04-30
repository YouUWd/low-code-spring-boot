package com.jdec.platform.config.api.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 权限配置条目响应 DTO
 * 用于列表展示
 */
@Data
public class SysPermissionConfigEntryResp {

    /**
     * 权限配置 ID
     */
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 权限值: 0-7 (READ:1, CREATE:2, UPDATE:4)
     */
    private Integer permission;

    /**
     * 权限状态: active-启用, inactive-禁用
     */
    private String permissionStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createdDate;

    /**
     * 更新时间
     */
    private LocalDateTime updatedDate;
}
