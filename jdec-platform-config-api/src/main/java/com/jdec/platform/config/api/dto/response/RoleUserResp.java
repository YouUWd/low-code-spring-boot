package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色关联用户信息VO
 */
@Data
@Schema(description = "角色关联用户信息")
public class RoleUserResp {

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "用户ID(带U前缀)")
    private String userId;

    @Schema(description = "用户头像")
    private String userAvatar;

    @Schema(description = "用户工号")
    private String workNumber;
}
