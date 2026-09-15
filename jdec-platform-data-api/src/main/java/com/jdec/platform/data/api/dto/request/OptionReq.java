package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
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
public class OptionReq implements Serializable {

    @NotNull(message = "模块 ID (moduleId) 不能为空")
    @Schema(description = "当前模块 ID，确保候选项严格属于当前模块上下文与数据隔离范围", example = "101")
    private Long moduleId;

    @NotNull(message = "字段 ID (fieldId) 不能为空")
    @Schema(description = "目标字段 ID (sys_module_field.id)，必须属于当前模块或其子孙模块", example = "1031")
    private Long fieldId;

    @Schema(description = "平铺过滤条件列表 (fieldId 必须属于当前模块或其子模块)")
    private List<FilterItem> filters;
}
