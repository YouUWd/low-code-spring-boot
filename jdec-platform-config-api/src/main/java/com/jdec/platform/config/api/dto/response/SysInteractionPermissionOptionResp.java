package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 交互权限下拉选项响应 */
@Data
@Schema(description = "交互权限下拉选项响应")
public class SysInteractionPermissionOptionResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "权限名称")
    private String name;

    @Schema(description = "权限编码")
    private String code;
}
