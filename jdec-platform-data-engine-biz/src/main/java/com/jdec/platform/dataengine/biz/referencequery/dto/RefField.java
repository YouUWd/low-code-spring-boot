package com.jdec.platform.dataengine.biz.referencequery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** 引用字段JSON结构模型类 */
@Data
@Builder
public class RefField {

    @Schema(description = "显示名称")
    private String label;

    @Schema(description = "版本号")
    private Integer version_no;

    @Schema(description = "字段值")
    private String value;

    @Schema(description = "额外数据")
    private Map<String, Object> extra_data;
}
