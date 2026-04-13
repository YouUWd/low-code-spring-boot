package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块权限定义 Entity
 *
 * <p>对应数据库表: sys_module_permission
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("sys_module_permission")
public class SysModulePermission {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 字段编码，* 表示模块级 */
    private String fieldCode;

    /** 权限类型 (READ/WRITE/UPDATE) */
    private String permissionType;

    /** 权限编码 (全局唯一) */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 权限描述 */
    private String description;

    /** 是否启用 */
    private Integer isEnabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
