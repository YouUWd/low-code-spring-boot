package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 权限节点列表查询请求 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "权限节点列表查询请求")
public class QuerySysRightReq extends PageRequest {

    @Schema(description = "权限节点名称（模糊查询）")
    private String rightName;
}
