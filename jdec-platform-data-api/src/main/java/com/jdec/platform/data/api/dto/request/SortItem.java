package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 结构化排序规则项 (SortItem，基于统一 table.column 驱动) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "结构化排序规则项")
public class SortItem implements Serializable {

    @Schema(
            description = "统一字段标识 (table.column 格式，如 info.created_date)",
            example = "info.created_date")
    @JsonProperty("field")
    private String field;

    @Schema(description = "排序方向: ASC (升序), DESC (降序)，默认 DESC", example = "DESC")
    @Builder.Default
    @JsonProperty("direction")
    private String direction = "DESC";

    @JsonIgnore
    public String getTableName() {
        if (field != null && field.contains(".")) {
            return field.substring(0, field.indexOf('.')).trim();
        }
        return null;
    }

    @JsonIgnore
    public String getColumnName() {
        if (field != null && field.contains(".")) {
            return field.substring(field.indexOf('.') + 1).trim();
        }
        return field != null ? field.trim() : null;
    }

    // ==================== 便捷工厂 ====================

    public static SortItem asc(String field) {
        return SortItem.builder().field(field).direction("ASC").build();
    }

    public static SortItem desc(String field) {
        return SortItem.builder().field(field).direction("DESC").build();
    }
}
