package com.jdec.platform.auth.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色信息（迁移自 student-manage 的 RoleResp，字段与源契约保持一致） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色信息")
public class RoleResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色标识")
    private String roleSlug;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "生效类型")
    private Integer effectiveType;

    @Schema(description = "生效开始日期（yyyy-MM-dd）")
    private String effectiveStartDate;

    @Schema(description = "生效结束日期（yyyy-MM-dd）")
    private String effectiveEndDate;

    @Schema(description = "角色来源")
    private Integer roleSource;
}
