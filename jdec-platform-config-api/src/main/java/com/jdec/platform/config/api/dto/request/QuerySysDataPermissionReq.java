package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限查询请求 */
@Data
@Schema(description = "数据权限查询请求")
public class QuerySysDataPermissionReq {

    @Schema(description = "权限业务类型编码")
    private String bizTypeCode;

    @Schema(description = "业务ID")
    private Integer bizId;
}
