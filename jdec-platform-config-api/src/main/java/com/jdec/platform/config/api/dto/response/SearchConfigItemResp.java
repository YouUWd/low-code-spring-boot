package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段搜索条件响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段搜索条件")
public class SearchConfigItemResp {

    /** 搜索操作编码，如 LIKE、IN、BETWEEN、RANGE */
    @Schema(description = "搜索操作编码", example = "LIKE")
    private String code;

    /** 搜索操作名称，如 模糊查询 */
    @Schema(description = "搜索操作名称", example = "模糊查询")
    private String name;

    /** 前端组件，如 text、multipleSelect */
    @Schema(description = "前端组件", example = "text")
    private String component;
}
