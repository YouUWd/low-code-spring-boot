package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.Data;

/** 用户角色列表响应 */
@Data
@Schema(description = "用户角色列表响应")
public class UserRoleListResp implements Serializable {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色标识")
    private String roleSlug;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "时效类型：1-永久，2-自定义")
    private Integer effectiveType;

    @Schema(description = "时效开始时间")
    private LocalDate effectiveStartDate;

    @Schema(description = "时效结束时间")
    private LocalDate effectiveEndDate;

    @Schema(description = "角色来源：1-用户直接关联，2-主体关联")
    private Integer roleSource;
}
