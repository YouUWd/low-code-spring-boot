package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 业务系统响应 */
@Data
@Schema(description = "业务系统响应")
public class BizSystemResponse {

    @Schema(description = "状态码", example = "200")
    private Integer status;

    @Schema(description = "消息", example = "ok")
    private String msg;

    @Schema(description = "数据列表")
    private List<BizDataPermissionResp> data;
}
