package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表关联关系节点")
public class TabNodeDTO implements java.io.Serializable {

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "父表名")
    private String parentTableName;

    @Schema(description = "关联类型,一般left join")
    private String joinType;

    @Schema(description = "父表关联字段")
    private String parentTableField;

    @Schema(description = "子表关联字段")
    private String subTableField;
}
