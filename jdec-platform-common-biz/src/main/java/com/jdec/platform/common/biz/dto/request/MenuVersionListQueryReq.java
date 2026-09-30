package com.jdec.platform.common.biz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "版本列表查询参数请求模型")
public class MenuVersionListQueryReq {

    @Schema(description = "详情数据ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long id;

    @Schema(description = "顶层菜单id", requiredMode = Schema.RequiredMode.REQUIRED, example = "19")
    private Long menuId;

    @Schema(description = "详情模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "view")
    private String btnType;
}
