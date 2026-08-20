package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 排序规则 */
@Data
@Schema(description = "排序规则")
public class SortRule {

    @Schema(description = "字段名", example = "id", required = true)
    private String field;

    @Schema(
            description = "排序方向",
            example = "DESC",
            required = true,
            allowableValues = {"ASC", "DESC"})
    private String order;
}
