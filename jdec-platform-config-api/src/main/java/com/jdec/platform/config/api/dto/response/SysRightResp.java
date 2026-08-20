package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 权限节点列表响应 */
@Data
@Schema(description = "权限节点列表")
public class SysRightResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "父级ID")
    private Long pid;

    @Schema(description = "权限节点名称")
    private String rightName;

    @Schema(description = "权限标识")
    private String rightSlug;

    @Schema(description = "节点类型.1:数据权限,2:交互权限,3:其它权限")
    private Integer nodeType;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "父级权限节点名称")
    private String parentRightName;

    @Schema(description = "父级权限节点标识")
    private String parentRightSlug;

    @Schema(description = "交互权限类型.1:申请 2:编辑 3:查看（仅交互权限有效）")
    private Integer type;
}
