package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 模块交互权限树响应 */
@Data
@Schema(description = "模块交互权限树节点")
public class SysModuleInteractionPermissionResp {

    @Schema(description = "节点唯一键（模块树节点用模块ID，虚拟类型节点用 moduleId_type）")
    private String nodeKey;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "模块ID（虚拟类型节点与真实模块节点均填充）")
    private Long moduleId;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(
            description = "交互权限类型（仅虚拟类型节点有值：1-申请 2-编辑 3-查看）",
            nullable = true)
    private Integer type;

    @Schema(description = "该节点关联的交互权限列表（仅虚拟类型节点有值）", nullable = true)
    private List<SysInteractionPermissionResp> permissions;

    @Schema(description = "子节点列表", nullable = true)
    private List<SysModuleInteractionPermissionResp> children;
}
