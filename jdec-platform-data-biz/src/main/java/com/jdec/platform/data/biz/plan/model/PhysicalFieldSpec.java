package com.jdec.platform.data.biz.plan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经元数据编译解析后的物理字段规格
 *
 * <p>以 fieldId (sys_module_field.id) 为唯一锚点，严格映射到数据库物理表名和物理列名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "物理字段规格说明")
public class PhysicalFieldSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模块字段配置 ID */
    private Long fieldId;

    /** 物理表名 */
    private String tableName;

    /** 物理列名 */
    private String columnName;

    /** 字段显示名称 */
    private String displayName;

    /** 排序顺序 */
    private Integer sortOrder;
}
