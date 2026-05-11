package com.jdec.platform.auth.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/** 登录响应 */
@Data
@Builder
@Schema(description = "登录响应")
public class LoginResp {

    @Schema(description = "Token")
    private String token;

    @Schema(description = "Token 名称")
    private String tokenName;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "是否超级管理员")
    private Boolean superAdmin;

    @Schema(description = "项目编号")
    private String projectNo;
}
