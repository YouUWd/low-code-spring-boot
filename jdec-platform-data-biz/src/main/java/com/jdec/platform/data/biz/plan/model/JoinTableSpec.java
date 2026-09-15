package com.jdec.platform.data.biz.plan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 伴生表 (1:1 或 N:1) 物理 JOIN 规格说明
 *
 * <p>基于 sys_table_relation 单向定义的物理关系推导生成，用于在主表查询时执行 LEFT JOIN 投影伴生字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "伴生表物理 JOIN 规格说明")
public class JoinTableSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待关联的目标表名 (如 clazz 或 student_profile) */
    private String targetTable;

    /** 目标表关联列名 (如 clazz.id 或 student_profile.student_id) */
    private String targetField;

    /** 宿主主表名 (如 student) */
    private String sourceTable;

    /** 宿主主表关联列名 (如 student.clazz_id 或 student.id) */
    private String sourceField;

    /** 关系类型 (1:1 或 N:1) */
    private String relationType;
}
