package com.jdec.platform.common_business.biz.auth.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 当前用户信息 * */
@Data
@EqualsAndHashCode
@Schema(description = "当前用户信息数据类")
public class LoginUserInfo implements Serializable {

    // 对应LoginUserUtil的getRealLoginUserId
    @Schema(description = "当前登录用户ID")
    private Long loginUserId;

    @Schema(description = "当前登录用户名称")
    private String loginUserName;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟用户名称")
    private String mockUserName;

    // 对应LoginUserUtil的getLoginUserId
    @Schema(description = "当前操作用户ID(有模拟是模拟人的ID)")
    private Long currentOptUserId;

    @Schema(description = "当前操作用户ID(有模拟是模拟人的ID)")
    private String currentOptUserName;

    @Schema(description = "当前主体ID")
    private Long subjectId;

    @Schema(description = "当前主体名称")
    private String subjectName;

    @Schema(description = "当前用户角色ID(有模拟为模拟人角色ID)")
    private Long roleId;

    @Schema(description = "当前用户角色名称(有模拟为模拟人角色名称)")
    private String roleName;

    @Schema(description = "当前是否模拟")
    private Boolean mocked = Boolean.FALSE;
}
