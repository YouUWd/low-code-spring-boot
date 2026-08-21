package com.jdec.platform.config.api.engine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "动态数据分页响应")
public class DataPageResp {
    @Schema(description = "当前页码")
    private Integer pageNum;

    @Schema(description = "每页大小")
    private Integer pageSize;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "数据列表")
    private List<Map<String, Object>> list;
}
