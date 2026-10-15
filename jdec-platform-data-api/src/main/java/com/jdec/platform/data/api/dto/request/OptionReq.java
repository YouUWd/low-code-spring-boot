package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 业务字段搜索下拉候选项请求 DTO (OptionReq) */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务字段搜索下拉候选项请求参数")
public class OptionReq implements Serializable {

    @NotNull(message = "查询主模块 ID 不能为空")
    @Schema(description = "当前模块 ID，用于锚定根模块上下文与数据隔离范围", example = "1001")
    private Long moduleId;

    @Schema(description = "目标物理表名 (为 null 或空时默认为当前主模块的物理主表)", example = "info")
    private String tableName;

    @NotBlank(message = "目标物理列名不能为空")
    @Schema(description = "需要查询去重候选项的物理列名 (如 reg_status, industry_types)", example = "reg_status")
    private String columnName;

    @Schema(description = "基于物理表与列名的过滤条件列表")
    private List<FilterItem> filters;

    @Schema(description = "候选项排序方向: ASC (升序) / DESC (降序)，为 null 或空时默认 ASC", example = "ASC")
    private String direction;

    @Schema(description = "最大返回候选项数量限制，默认为 100", example = "100")
    private Integer limit;
}
