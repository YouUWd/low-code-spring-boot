package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;

/** 角色关联用户信息VO */
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

    @Schema(description = "性别 1-男 2-女")
    private Integer sex;

    @Schema(description = "有效期类型：1-永久，2-自定义")
    private Integer effectiveType;

    @Schema(description = "有效期开始")
    private LocalDate effectiveStartDate;

    @Schema(description = "有效期结束")
    private LocalDate effectiveEndDate;

    @Schema(description = "状态(0-有效，1-失效)")
    private Integer status;
}
