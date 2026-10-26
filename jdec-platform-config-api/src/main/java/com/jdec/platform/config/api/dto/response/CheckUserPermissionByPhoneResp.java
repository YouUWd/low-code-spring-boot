package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通过手机号检查用户权限响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通过手机号检查用户权限响应")
public class CheckUserPermissionByPhoneResp {

    @Schema(description = "是否有权限")
    private Boolean permissionFlag;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "员工id")
    private Long userId;

    @Schema(description = "公司id")
    private Long companyId;

    @Schema(description = "主体id")
    private Long subjectId;

    @Schema(description = "用户类型 1内部 2外部")
    private Integer userType;

    @Schema(description = "名称")
    private String userName;

    @Schema(description = "工号")
    private String workNumber;

    @Schema(description = "头像")
    private String userAvatar;

    @Schema(description = "手机")
    private String phone;

    @Schema(description = "性别")
    private Integer sex;

    @Schema(description = "所属公司/内部人员为主体")
    private String companyName;

    @Schema(description = "部门名称")
    private String departName;

    @Schema(description = "部门id")
    private String deptIds;

    @Schema(description = "部门领导人")
    private String leaderName;

    @Schema(description = "职位名称")
    private String positionName;

    @Schema(description = "主体简称")
    private String subjectName;

    @Schema(description = "状态 1启用 2禁用")
    private Integer statusFlag;

    @Schema(description = "在职状态")
    private Integer employedStatus;

    @Schema(description = "是否超级人员 0-否 1-是")
    private Integer superFlag;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;
}
