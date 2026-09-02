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

/** 全局物理表关联关系实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_table_relation")
public class SysTableRelation {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 所属主体 ID */
    private Long subjectId;

    /** 主表名 (如 student / course) */
    private String mainTable;

    /** 主表关联字段 (如 id 或 clazz_id) */
    private String mainField;

    /** 被关联表名 (如 student_profile / clazz / student_course) */
    private String joinTable;

    /** 被关联表关联字段 (如 student_id 或 id) */
    private String joinField;

    /** 关系类型: 1:1, 1:N, N:1 */
    private String relationType;

    /** 关联关系中文说明 */
    private String description;

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
