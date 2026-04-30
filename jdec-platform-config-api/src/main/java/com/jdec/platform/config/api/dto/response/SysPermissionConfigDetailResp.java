package com.jdec.platform.config.api.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 权限配置详情响应 DTO
 * 用于详情页面
 */
@Data
public class SysPermissionConfigDetailResp {

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
     * 模块 ID
     */
    private Long moduleId;

    /**
     * 逻辑字段名
     */
    private String logicalField;

    /**
     * 权限描述
     */
    private String description;

    /**
     * 权限状态: active-启用, inactive-禁用
     */
    private String permissionStatus;

    /**
     * 创建人 ID
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdDate;

    /**
     * 创建人姓名
     */
    private String createdName;

    /**
     * 更新人 ID
     */
    private String updatedBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedDate;

    /**
     * 更新人姓名
     */
    private String updatedName;
}
