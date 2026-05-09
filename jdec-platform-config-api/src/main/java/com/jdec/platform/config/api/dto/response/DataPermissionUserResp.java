package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限用户信息响应 */
@Data
@Schema(description = "数据权限用户信息响应")
public class DataPermissionUserResp {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户姓名")
    private String userName;

    @Schema(description = "工号")
    private String workNumber;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "部门名称")
    private String departmentName;

    @Schema(description = "企业微信用户ID")
    private String shortName;
}
