package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 权限配置实体 权限模型：位运算（READ:1, CREATE:2, UPDATE:4） */
@Data
@TableName("sys_permission_config")
public class SysPermissionConfig {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 模块 ID */
    private Long moduleId;

    /** 表名 */
    private String tableName;

    /** 逻辑字段名 */
    private String logicalField;

    /** 字段名称 */
    private String fieldName;

    /** 权限值: 0-7 (READ:1, CREATE:2, UPDATE:4) */
    private Integer permission;

    /** 权限描述 */
    private String description;

    /** 权限状态 */
    private String permissionStatus;

    /** 创建人 ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    private String createdName;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    private String updatedName;

    /** 是否删除 */
    private Integer deleted;
}
