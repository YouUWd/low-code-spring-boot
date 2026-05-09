package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 权限节点列表查询请求
 */
@Data
@Schema(description = "权限节点列表查询请求")
public class QuerySysRightReq {

    @Schema(description = "权限节点名称（模糊查询）")
    private String rightName;
}
