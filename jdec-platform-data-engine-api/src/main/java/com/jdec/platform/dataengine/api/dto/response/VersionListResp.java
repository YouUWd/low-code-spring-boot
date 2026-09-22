package com.jdec.platform.dataengine.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "详情版本列表返回数据")
public class VersionListResp {

    @Schema(description = "版本号", example = "版本号")
    private Integer version;

    @Schema(description = "历史版本或者当前版本", example = "历史版本或者当前版本")
    private Boolean history;
}
