package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 操作日志索引保存请求 */
@Data
@Schema(description = "操作日志索引保存请求")
public class MonitorOperateIndexSaveReq implements Serializable {

    @Schema(description = "日志id")
    private Long logId;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "单据id")
    private Long dataId;
}
