package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 角色配置响应VO
 */
@Data
@Schema(description = "角色配置响应")
public class RoleConfigPageResp {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户列表")
    private List<RoleUserResp> userList;

    @Schema(description = "主体ID列表")
    private List<RoleSubjectResp> subjectList;

    @Schema(description = "时效类型：1-永久，2-自定义")
    private Integer effectiveType;

    @Schema(description = "时效开始时间")
    private LocalDate effectiveStartDate;

    @Schema(description = "时效结束时间")
    private LocalDate effectiveEndDate;
}
