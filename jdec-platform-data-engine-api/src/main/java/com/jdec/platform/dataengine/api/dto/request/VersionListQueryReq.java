package com.jdec.platform.dataengine.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "版本列表查询参数请求模型")
public class VersionListQueryReq {

    @Schema(description = "详情数据ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long id;

    @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long moduleId;

    @Schema(description = "详情模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "view")
    private String btnType;
}
