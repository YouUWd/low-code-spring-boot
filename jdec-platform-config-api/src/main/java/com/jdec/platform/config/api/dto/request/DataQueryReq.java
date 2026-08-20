package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 动态查询请求对象 */
@Data
@Schema(description = "动态查询请求")
public class DataQueryReq {

    @Schema(description = "页码（从1开始）", example = "1", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "每页大小", example = "20", defaultValue = "20")
    private Integer pageSize = 20;

    @Schema(description = "排序规则列表（支持多字段排序）")
    private List<SortRule> sorts;

    @Schema(description = "过滤条件列表（多条件之间使用 AND 连接）")
    private List<FilterRule> filters;
}
