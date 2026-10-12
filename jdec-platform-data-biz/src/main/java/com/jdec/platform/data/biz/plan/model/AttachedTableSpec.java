package com.jdec.platform.data.biz.plan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 附加表物理 JOIN 规格说明 (AttachedTableSpec)
 *
 * <p>覆盖两类由模块主表左连接进来的附加表：
 *
 * <ul>
 *   <li>伴生表（垂直拆分 1:1）—— 沿关系向下，ON 为本表出主键；
 *   <li>上游表（多对一 N:1）—— 沿关系向上，ON 为本表出外键。
 * </ul>
 *
 * <p>两类共用 {@code source.sourceField = target.targetField} 的 ON 形式，仅出键方向相反， 因此执行侧无需分支。 基于
 * sys_table_relation 单向定义的物理关系推导生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "附加表物理 JOIN 规格说明")
public class AttachedTableSpec {

    /** 待关联的目标表名 (如 clazz 或 student_profile) */
    private String targetTable;

    /** 目标表关联列名 (如 clazz.id 或 student_profile.student_id) */
    private String targetField;

    /** 宿主主表名 (如 student) */
    private String sourceTable;

    /** 宿主主表关联列名 (如 student.clazz_id 或 student.id) */
    private String sourceField;

    /**
     * 附加方式：{@code 1:1} 伴生表 / {@code N:1} 上游表。
     *
     * <p>已按宿主表视角归一化 —— 元数据 sys_table_relation.relation_type 记的是 {@code '1:N'} （main_table
     * 为「一」端），此处方向相反，勿直接比对。
     */
    private String joinKind;
}
