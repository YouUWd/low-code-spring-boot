package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/** 创建权限配置请求 DTO 权限模型：位运算（READ:1, CREATE:2, UPDATE:4） */
@Data
public class CreateSysPermissionConfigReq {

    /** 租户 ID，0 表示系统共享权限 */
    private Long tenantId;

    /** 模块 ID（可选，用于模块级权限） */
    private Long moduleId;

    /** 表名 */
    private String tableName;

    /** 物理字段名称 */
    private String fieldName;

    /** 逻辑字段名 */
    private String logicalField;

    /** 权限值: 0-7 (READ:1, CREATE:2, UPDATE:4) */
    private Integer permission;

    /** 权限描述 */
    private String description;

    /** 创建人 ID */
    private String createdBy;

    /** 创建人姓名 */
    private String createdName;
}
