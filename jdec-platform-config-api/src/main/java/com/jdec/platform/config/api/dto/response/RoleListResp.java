package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

@Data
public class RoleListResp implements Serializable {
    @Schema(description = "序号")
    private Integer xh;

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色标识")
    private String roleSlug;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "应用编码")
    private String projectNo;
}
