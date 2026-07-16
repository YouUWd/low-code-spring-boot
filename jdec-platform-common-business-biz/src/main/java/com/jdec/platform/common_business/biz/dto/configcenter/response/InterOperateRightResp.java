package com.jdec.platform.common_business.biz.dto.configcenter.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "交互权限节点数据类")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterOperateRightResp {

    @Schema(description = "交互权限id")
    private Long permissionId;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "交互权限类型.1:申请 2:编辑 3:查看")
    private Integer type;

    @Schema(description = "交互权限名称")
    private String permissionName;

    @Schema(description = "交互权限编码")
    private String permissionCode;

    @Schema(description = "排序")
    private Integer sort;
}
