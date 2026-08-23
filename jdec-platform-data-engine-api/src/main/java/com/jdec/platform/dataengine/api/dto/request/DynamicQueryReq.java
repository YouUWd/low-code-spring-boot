package com.jdec.platform.dataengine.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态列表分页查询请求模型")
public class DynamicQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "128")
    private Long moduleId;

    @Schema(description = "页码 (从 1 开始)", example = "1")
    @Builder.Default
    private int pageIndex = 1;

    @Schema(description = "每页条数", example = "20")
    @Builder.Default
    private int pageSize = 20;

    @Schema(description = "动态查询过滤条件键值对 (支持主表及关联表字段)")
    private Map<String, Object> filters;

    @Schema(description = "排序字段名称", example = "create_date")
    private String orderBy;

    @Schema(description = "排序方向: ASC 或 DESC", example = "DESC")
    private String orderDirection;
}
