package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;

@Data
@Schema(description = "角色关联主体信息")
public class RoleSubjectResp {
    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "有效期类型：1-永久，2-自定义")
    private Integer effectiveType;

    @Schema(description = "有效期开始")
    private LocalDate effectiveStartDate;

    @Schema(description = "有效期结束")
    private LocalDate effectiveEndDate;

    @Schema(description = "状态(0-有效,1-失效)")
    private Integer status;
}
