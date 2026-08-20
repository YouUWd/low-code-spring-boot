package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 组合字段配置保存请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组合字段配置保存请求")
public class SysFieldCombineInfoReq {

    @Schema(description = "物理多表映射列表")
    private List<SysFieldSourceMappingReq> sourceMapping;

    @Schema(description = "转换表达式")
    private String transformer;
}
