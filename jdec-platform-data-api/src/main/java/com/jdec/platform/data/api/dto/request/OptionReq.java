package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jdec.platform.data.api.model.predicate.Predicate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
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

    @Schema(description = "业务视图编码 (可选)", example = "COMPANY")
    @JsonProperty("view")
    private String view;

    @Schema(description = "目标物理表名 (为 null 或空时默认为当前主模块的物理主表)", example = "info")
    private String tableName;

    @NotBlank(message = "目标物理列名不能为空")
    @Schema(description = "需要查询去重候选项的物理列名 (如 reg_status, industry_types)", example = "reg_status")
    private String columnName;

    @Schema(description = "基于 MySQL 标准布尔抽象语法树的过滤条件")
    @JsonProperty("filter")
    @JsonDeserialize(using = FilterDeserializer.class)
    private Predicate filter;

    @Schema(description = "候选项排序方向: ASC (升序) / DESC (降序)，为 null 或空时默认 ASC", example = "ASC")
    private String direction;

    public Predicate getFilters() {
        return filter;
    }

    public void setFilters(Predicate f) {
        this.filter = f;
    }
}
