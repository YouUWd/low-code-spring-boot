package com.jdec.platform.dataengine.biz.operatelog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作日志 - 数据行变更（一条记录的新增/更新/删除） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "操作日志数据行变更")
public class RowChangeLogDTO implements Serializable {

    @Schema(description = "唯一值（模块配置的「代表业务唯一性字段」取旧数据中的值，按「【字段1-字段2-字段3】」拼接）")
    private String name;

    @Schema(description = "本次变更的字段明细")
    private List<ColumnChangeLogDTO> columns;

    @Schema(description = "当前行下面的子模块变更")
    private List<ModuleChangeLogDTO> children;
}
