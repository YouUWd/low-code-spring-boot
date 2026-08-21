package com.jdec.platform.engine.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "动态数据查询请求")
public class DataQueryReq {
    @Schema(description = "当前页码", defaultValue = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", defaultValue = "10")
    private Integer pageSize = 10;

    @Schema(description = "查询条件 (字段名 -> 值)")
    private Map<String, Object> filters;

    @Schema(description = "排序字段及方向，例如 [\"create_time desc\"]")
    private List<String> sorts;
}
