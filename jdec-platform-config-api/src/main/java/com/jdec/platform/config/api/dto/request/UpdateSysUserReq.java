package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 用户编辑请求 */
@Data
@Schema(description = "用户编辑请求")
public class UpdateSysUserReq {

    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long id;

    @Schema(description = "姓名")
    @NotBlank(message = "姓名不能为空")
    private String userName;

    @Schema(description = "工号")
    private String workNumber;

    @Schema(description = "性别")
    @NotNull(message = "性别不能为空")
    private Integer sex;

    @Schema(description = "公司id")
    @NotNull(message = "所属公司不能为空")
    private Long companyId;

    @Schema(description = "所属公司")
    private String companyName;

    @Schema(description = "部门ids")
    private String departIds;

    @Schema(description = "所属部门")
    private String departName;

    @Schema(description = "岗位")
    private String positionName;

    @Schema(description = "部门领导人")
    private String leaderName;

    @Schema(description = "手机号")
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @Schema(description = "在职状态")
    private Integer employedStatus;

    @Schema(description = "头像")
    private String userAvatar;
}
