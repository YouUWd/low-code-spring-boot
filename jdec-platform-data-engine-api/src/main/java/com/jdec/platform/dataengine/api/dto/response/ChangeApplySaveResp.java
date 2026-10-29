package com.jdec.platform.dataengine.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ChangeApplySaveResp {

    @Schema(description = "根模块ID")
    private Long moduleId;

    @Schema(description = "根模块数据ID")
    private Long dataId;

    @Schema(description = "根模块数据业务编号")
    private String businessNo;

    @Schema(description = "根模块数据版本")
    private Integer versionNo;

    @Schema(description = "变更申请ID")
    private Long changeApplyId;
}
