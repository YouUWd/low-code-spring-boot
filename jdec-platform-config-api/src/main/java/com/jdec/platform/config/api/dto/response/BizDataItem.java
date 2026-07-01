package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 业务数据项 */
@Data
@Schema(description = "业务数据项")
public class BizDataItem {

    @Schema(description = "业务ID")
    private String bizId;

    @Schema(description = "业务名称")
    private String bizName;
}
