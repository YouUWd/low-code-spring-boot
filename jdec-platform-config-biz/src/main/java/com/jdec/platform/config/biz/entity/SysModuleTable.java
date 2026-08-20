package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 模块关联物理表实体 */
@Data
@TableName("sys_module_table")
public class SysModuleTable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 表名 */
    private String tableName;

    /** 表描述 */
    private String tableDesc;

    /** 关联左字段 */
    private String joinLeftField;

    /** 关联右字段 */
    private String joinRightField;

    /** 关系类型 */
    private String relationType;

    /** 关联表是否只读：0-否, 1-是 */
    private Integer readOnly;

    /** 排序顺序 */
    private Integer sortOrder;

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
}
