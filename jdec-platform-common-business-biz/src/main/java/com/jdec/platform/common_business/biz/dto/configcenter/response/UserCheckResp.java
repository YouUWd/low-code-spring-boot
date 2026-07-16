package com.jdec.platform.common_business.biz.dto.configcenter.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 判断用户手机号对应的用户是否在本系统有角色和登录权限接口返回数据类
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCheckResp {
    @Schema(description = "用户在本系统是否有角色")
    private Boolean permissionFlag;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "主键id")
    private Long id;

    @Schema(description = "公司id")
    private Long companyId;

    @Schema(description = "主体id")
    private Short subjectId;

    @Schema(description = "用户类型 1内部 2外部")
    private Long userType;

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "用户工号")
    private String workNumber;

    @Schema(description = "用户状态 1启用 2禁用")
    private Integer statusFlag;

    @Schema(description = "用户部门id")
    private String deptIds;

    @Schema(description = "用户部门名称")
    private String departName;
}
