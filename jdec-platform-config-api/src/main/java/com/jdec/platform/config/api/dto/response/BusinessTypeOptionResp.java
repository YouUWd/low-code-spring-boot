package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 业务类型下拉选项 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务类型下拉选项")
public class BusinessTypeOptionResp {

    @Schema(description = "业务类型ID", example = "1")
    private Long id;

    @Schema(description = "业务类型标识", example = "descriptionText")
    private String businessTypeCode;

    @Schema(description = "业务类型名称", example = "描述文本")
    private String businessTypeName;
}
