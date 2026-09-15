package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动态列表/全景分页查询请求模型
 *
 * <p>调用方仅需传递目标根模块 ID 及平铺的 fields/filters/sorts。 后端根据模块拓扑自动推导整棵执行树，并严格限制所有字段属于当前模块自身或其子孙模块。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态列表/全景分页查询请求模型")
public class QueryReq implements Serializable {

    @NotNull(message = "查询主模块 ID 不能为空")
    @Schema(description = "根主模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "101")
    private Long moduleId;

    @Schema(description = "页码 (从 1 开始，针对当前模块列表)", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数 / 明细上限条数", example = "20")
    private Integer pageSize;

    @Schema(
            description =
                    "显式投影字段 ID 列表 (sys_module_field.id，限制为模块本身及子模块的字段)。为 null 或空时默认投影当前模块全部可见字段")
    private List<Long> fields;

    @Schema(description = "平铺过滤条件列表 (fieldId 必须属于当前模块或其子模块)")
    private List<FilterItem> filters;

    @Schema(description = "平铺排序规则列表 (fieldId 必须属于当前模块或其子模块)")
    private List<SortItem> sorts;
}
