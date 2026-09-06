package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
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

    @Schema(
            description = "视图模式: LIST(列表-严格按表头headers组装), DETAIL(详情-严格按模块物理字段fields组装)",
            example = "LIST")
    @Builder.Default
    private String viewMode = "LIST";

    @Schema(description = "页码 (从 1 开始)", example = "1")
    @Builder.Default
    private Integer pageNo = 1;

    @Schema(description = "每页条数", example = "20")
    @Builder.Default
    private Integer pageSize = 20;

    @Schema(description = "动态查询结构化过滤条件列表")
    private List<DynamicFilterItem> filters;

    @Schema(description = "结构化排序规则列表 (按顺序支持多字段复合排序)")
    private List<DynamicSortItem> sorts;
}
