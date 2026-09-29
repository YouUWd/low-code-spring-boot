package com.jdec.platform.common.biz.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 业务状态简要信息响应 (提取核心要素: title, statusValue, statusBackground, statusFontColor) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "业务状态简要信息响应")
public class StatusSimpleResp implements Serializable {

    @Schema(description = "状态名称", example = "已审批")
    private String title;

    @Schema(description = "状态值", example = "1")
    private Integer statusValue;

    @Schema(description = "状态背景色", example = "#e6f7ff")
    private String statusBackground;

    @Schema(description = "状态字体颜色", example = "#1890ff")
    private String statusFontColor;
}
