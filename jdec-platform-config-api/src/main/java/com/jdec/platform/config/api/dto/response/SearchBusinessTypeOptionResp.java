package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 业务类型下拉选项（取自 search_config.json，已按 businessType 去重） */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务类型下拉选项")
public class SearchBusinessTypeOptionResp {

    @Schema(description = "业务类型编码", example = "TEXT")
    private String businessType;

    @Schema(description = "业务类型名称", example = "文本")
    private String businessTypeName;
}
