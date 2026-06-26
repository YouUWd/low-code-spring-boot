package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限查询请求 */
@Data
@Schema(description = "数据权限查询请求")
public class QuerySysDataPermissionMappingReq {

    @Schema(description = "权限节点编码")
    private String dataCode;

    @Schema(description = "业务ID")
    private Integer bizId;
}
