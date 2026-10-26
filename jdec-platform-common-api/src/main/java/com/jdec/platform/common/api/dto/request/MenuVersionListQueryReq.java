package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本列表查询参数请求模型")
public class MenuVersionListQueryReq implements Serializable {

    @Schema(description = "详情数据ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long id;

    @Schema(description = "顶层菜单id", requiredMode = Schema.RequiredMode.REQUIRED, example = "19")
    private Long menuId;

    @Schema(description = "详情模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "view")
    private String btnType;

    @Schema(
            description = "数据业务编号",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "INFO2026092117293310001")
    private String businessNo;
}
