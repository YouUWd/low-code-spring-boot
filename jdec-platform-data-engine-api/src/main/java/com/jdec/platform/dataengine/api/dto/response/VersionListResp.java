package com.jdec.platform.dataengine.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "详情版本列表返回数据")
public class VersionListResp {

    @Schema(description = "数据ID", example = "100")
    private Long id;

    @Schema(description = "版本号", example = "版本号")
    private Integer version;

    @Schema(description = "上一版本号", example = "上一版本号")
    private Integer preVersion;

    @Schema(description = "历史版本或者当前版本", example = "历史版本或者当前版本")
    private Boolean history;

    @Schema(description = "查看类型", example = "查看类型")
    private String btnType;

    @Schema(description = "业务状态", example = "99")
    private Integer statusValue;
}
