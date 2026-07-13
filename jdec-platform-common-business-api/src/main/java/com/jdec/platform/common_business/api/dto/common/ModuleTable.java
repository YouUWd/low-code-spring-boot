package com.jdec.platform.common_business.api.dto.common;

import lombok.Builder;
import lombok.Data;

/** 模块表关联配置数据类 * */
@Data
@Builder
public class ModuleTable {
    private Long moduleId;

    private String tableName;

    private String joinLeftField;

    private String joinRightField;

    private String relationType;

    private String tableDesc;

    private Integer sortOrder;

    // 关联表是否只读（0=否，1=是）
    private Integer readOnly;
}
