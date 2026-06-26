package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 数据权限保存/编辑请求 */
@Data
@Schema(description = "数据权限保存/编辑请求")
public class SysDataPermissionMappingSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "权限节点编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限节点编码不能为空")
    private String dataCode;

    @Schema(description = "业务ID")
    private Long bizId;

    @Schema(description = "业务名称")
    private String bizName;
}
