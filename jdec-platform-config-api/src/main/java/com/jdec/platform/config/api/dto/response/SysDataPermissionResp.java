package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限列表响应 */
@Data
@Schema(description = "数据权限列表响应")
public class SysDataPermissionResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "权限业务类型编码")
    private String bizTypeCode;

    @Schema(description = "权限业务类型名称")
    private String bizTypeName;

    @Schema(description = "业务ID")
    private Long bizId;

    @Schema(description = "业务名称")
    private String bizName;
}
