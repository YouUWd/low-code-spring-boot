package com.jdec.platform.config.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 列头字段搜索控件配置 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "列头字段搜索控件配置")
public class MenuHeaderSearchTypeResp {

    @Schema(description = "搜索类型编码", example = "input")
    private String code;

    @Schema(description = "搜索类型名称", example = "文本输入")
    private String name;

    @Schema(description = "搜索控件组件", example = "Input")
    private String component;
}
