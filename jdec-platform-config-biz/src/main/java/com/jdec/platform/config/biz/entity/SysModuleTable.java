package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块关联物理表拓扑实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /** 是否主表: 1-主表, 0-从表 */
    private Integer isPrimary;

    /** 关联左字段 (从表外键) */
    private String joinLeftField;

    /** 关联右字段 (主表关联键) */
    private String joinRightField;

    /** 关系类型: PRIMARY, 1:1, N:1, 1:N */
    private String relationType;

    /** 关联表是否只读：0-否, 1-是 */
    private Integer readOnly;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新人 ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic private Integer deleted;
}
