package com.jdec.platform.dataengine.api.dto.request;

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
@Schema(description = "动态详情页查询请求模型")
public class DynamicDetailQueryReq implements Serializable {

    @Schema(description = "菜单 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "55")
    private Long menuId;

    @Schema(description = "数据 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "55")
    private Long id;

    @Schema(description = "版本 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "V2")
    private Integer version;

    @Schema(
            description = "按钮",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "change/view/change-change")
    private String btnType;
}
