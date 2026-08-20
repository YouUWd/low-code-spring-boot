package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 主体下拉选项 */
@Data
@Schema(description = "主体下拉选项")
public class SubjectOptionResp {

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "主体名称")
    private String subjectName;
}
