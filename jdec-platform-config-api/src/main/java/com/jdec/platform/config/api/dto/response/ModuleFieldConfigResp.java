package com.jdec.platform.config.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块字段配置响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleFieldConfigResp {
    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 字段类型 */
    private String fieldType;

    /** 是否可见 */
    private Boolean visible;

    /** 展示顺序 */
    private Integer sortOrder;

    /** 字段宽度 */
    private Integer width;

    /** 对齐方式 */
    private String align;
}
