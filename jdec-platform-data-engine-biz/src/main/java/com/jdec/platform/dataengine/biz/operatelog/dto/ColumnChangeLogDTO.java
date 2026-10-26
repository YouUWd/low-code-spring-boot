package com.jdec.platform.dataengine.biz.operatelog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作日志 - 字段变更明细 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "操作日志字段变更明细")
public class ColumnChangeLogDTO implements Serializable {

    @Schema(description = "字段中文名")
    private String name;

    @Schema(description = "字段旧值（保持原始类型，数组/对象不做字符串化）")
    private Object old;

    @Schema(description = "字段新值（保持原始类型，数组/对象不做字符串化）")
    private Object newer;

    @Schema(description = "字段英文名（表名.列名）")
    private String field;

    @Schema(description = "字段ID（sys_module_field.id，便于查询侧做字段权限过滤）")
    private String fieldId;
}
