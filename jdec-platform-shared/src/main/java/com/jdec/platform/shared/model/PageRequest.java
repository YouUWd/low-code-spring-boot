package com.jdec.platform.shared.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 分页请求基类 */
@Data
@Schema(description = "分页请求")
public class PageRequest {

    @Schema(description = "当前页码", defaultValue = "1")
    @Min(value = 1, message = "页码最小为1")
    private Long pageNum = 1L;

    @Schema(description = "每页条数", defaultValue = "10")
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Long pageSize = 10L;

    @Schema(description = "排序字段")
    private String orderBy;

    @Schema(description = "排序方式: asc/desc", defaultValue = "desc")
    private String orderType = "desc";
}
