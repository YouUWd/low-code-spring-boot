package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色配置响应VO */
@Data
@Schema(description = "角色配置响应")
public class RoleConfigPageResp {

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户列表（每项含各自时效信息）")
    private List<RoleUserResp> userList;

    @Schema(description = "主体列表（每项含各自时效信息）")
    private List<RoleSubjectResp> subjectList;
}
