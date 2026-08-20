package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 权限节点保存/编辑请求 */
@Data
@Schema(description = "权限节点保存/编辑请求")
public class SysRightSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "父级ID，顶级为0")
    private Long pid;

    @Schema(description = "权限节点名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限节点名称不能为空")
    private String rightName;

    @Schema(description = "权限标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限标识不能为空")
    private String rightSlug;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "类型.1:数据权限,2:交互权限,3:其它权限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "节点类型不能为空")
    private Integer nodeType;
}
