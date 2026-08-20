package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限类型下拉选项响应 */
@Data
@Schema(description = "数据权限类型下拉选项响应")
public class SysDataPermissionOptionResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "类型名称")
    private String name;

    @Schema(description = "类型编码")
    private String code;
}
