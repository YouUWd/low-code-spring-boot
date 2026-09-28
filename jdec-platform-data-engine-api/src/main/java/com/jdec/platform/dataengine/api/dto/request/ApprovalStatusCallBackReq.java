package com.jdec.platform.dataengine.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

@Data
@Schema(description = "审批链状态回调")
public class ApprovalStatusCallBackReq implements Serializable {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "数据ID")
    private Long dataId;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "业务状态")
    private Integer businessStatus;

    @Schema(description = "是否终止状态")
    private Boolean isTermination;
}
