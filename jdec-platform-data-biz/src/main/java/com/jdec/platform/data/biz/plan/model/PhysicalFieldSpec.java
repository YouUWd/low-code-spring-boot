package com.jdec.platform.data.biz.plan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经元数据编译解析后的物理字段规格
 *
 * <p>以物理表名 + 物理列名作为唯一锚点，严格映射到数据库物理表与物理列。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "物理字段规格说明")
public class PhysicalFieldSpec {

    /** 物理表名 */
    private String tableName;

    /** 物理列名 */
    private String columnName;

    /** 字段显示名称 */
    private String displayName;

    /** 排序顺序 */
    private Integer sortOrder;
}
