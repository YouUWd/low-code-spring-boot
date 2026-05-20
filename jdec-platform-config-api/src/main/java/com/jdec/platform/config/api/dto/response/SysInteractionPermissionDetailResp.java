package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 交互权限详情响应 */
@Data
@Schema(description = "交互权限详情响应")
public class SysInteractionPermissionDetailResp {

    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "模块标识")
    private String moduleCode;

    @Schema(description = "交互权限类型.1:申请 2:编辑 3:查看")
    private Integer type;

    @Schema(description = "权限名称")
    private String name;

    @Schema(description = "权限编码")
    private String code;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "应用编码")
    private String projectNo;

    @Schema(description = "创建时间")
    private String createdDate;

    @Schema(description = "创建人名称")
    private String createdName;

    @Schema(description = "更新时间")
    private String updatedDate;

    @Schema(description = "更新人名称")
    private String updatedName;
}
