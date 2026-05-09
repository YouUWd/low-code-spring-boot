package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "角色关联主体信息")
public class RoleSubjectResp {
    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "主体名称")
    private String subjectName;
}
