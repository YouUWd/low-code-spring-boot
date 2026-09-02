package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色模块字段权限配置实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_module_field_permission")
public class SysRoleModuleFieldPermission implements Serializable {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 模块ID */
    private Long moduleId;

    /** 物理表名 */
    private String tableName;

    /** 物理列名 */
    private String columnName;

    /** 是否可申请/填报(新增): 0-否, 1-是 */
    private Integer apply;

    /** 是否可查看/浏览: 0-否, 1-是 */
    private Integer view;

    /** 是否可编辑/修改: 0-否, 1-是 */
    private Integer edit;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    private String createdName;

    /** 更新人ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    private String updatedName;
}
