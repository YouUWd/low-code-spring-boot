package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 权限节点下拉选项响应 */
@Data
@Schema(description = "权限节点下拉选项")
public class SysRightOptionResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "权限节点名称")
    private String rightName;

    @Schema(description = "权限标识")
    private String rightSlug;
}
