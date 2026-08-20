package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 组合字段配置响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组合字段配置响应")
public class SysFieldCombineInfoResp {

    @Schema(description = "物理多表映射列表")
    private List<SysFieldSourceMappingResp> sourceMapping;

    @Schema(description = "转换表达式")
    private String transformer;
}
