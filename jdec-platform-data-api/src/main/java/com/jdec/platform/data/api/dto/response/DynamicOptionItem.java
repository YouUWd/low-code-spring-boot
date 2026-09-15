package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用字段搜索下拉候选项响应 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用字段搜索下拉候选项条目")
public class DynamicOptionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "选项显示文本 (展示文案/字典名称/枚举标签等)", example = "新选课程1")
    private String label;

    @Schema(description = "选项实际值 (原始值/编码/ID等)", example = "新选课程1")
    private Object value;
}
