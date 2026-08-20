package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.dto.request.RoleSubjectReq;
import com.jdec.platform.config.api.dto.request.RoleUserReq;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Schema(description = "角色配置详情")
@Data
public class RoleConfigDetailResp {
    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "主体列表（每项含各自时效信息）")
    private List<RoleSubjectReq> subjects;

    @Schema(description = "内部用户列表（每项含各自时效信息）")
    private List<RoleUserReq> insideUsers;

    @Schema(description = "外部用户列表（每项含各自时效信息）")
    private List<RoleUserReq> externalUsers;

    @Schema(description = "主体ID列表")
    private List<Long> subjectIds;

    @Schema(description = "内部用户ID列表（字符串格式）")
    private List<String> userInsideIds;

    @Schema(description = "外部用户ID列表（字符串格式）")
    private List<String> userExternalIds;
}
