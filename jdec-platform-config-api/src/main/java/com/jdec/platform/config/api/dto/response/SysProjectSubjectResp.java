package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 项目主体关联信息响应 */
@Data
@Schema(description = "项目主体关联信息响应")
public class SysProjectSubjectResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "项目编码")
    private String projectNo;
}
