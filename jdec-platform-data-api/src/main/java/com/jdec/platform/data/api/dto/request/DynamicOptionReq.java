package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用字段搜索下拉候选项请求 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用字段搜索下拉候选项请求参数")
public class DynamicOptionReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "模块 ID (moduleId) 不能为空")
    @Schema(description = "当前模块 ID，确保候选项严格属于当前模块上下文与数据隔离范围", example = "101")
    private Long moduleId;

    @NotBlank(message = "物理表名 (tableName) 不能为空")
    @Schema(description = "目标物理表名", example = "student_course")
    private String tableName;

    @NotBlank(message = "物理列名 (columnName) 不能为空")
    @Schema(description = "目标物理列名", example = "course_name")
    private String columnName;

    @Schema(description = "搜索关键词 (可选，用于模糊匹配候选项)", example = "新选")
    private String keyword;
}
