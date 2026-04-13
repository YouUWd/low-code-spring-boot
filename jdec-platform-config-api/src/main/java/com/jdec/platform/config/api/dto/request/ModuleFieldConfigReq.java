package com.jdec.platform.config.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块字段配置请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleFieldConfigReq {
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

    public void validate() {
        if (fieldCode == null || fieldCode.trim().isEmpty()) {
            throw new IllegalArgumentException("字段编码不能为空");
        }
        if (fieldName == null || fieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("字段名称不能为空");
        }
        if (visible == null) {
            throw new IllegalArgumentException("可见性不能为空");
        }
        if (sortOrder == null || sortOrder < 0) {
            throw new IllegalArgumentException("展示顺序必须大于等于 0");
        }
        if (width != null && width <= 0) {
            throw new IllegalArgumentException("字段宽度必须大于 0");
        }
        if (align != null && !align.matches("^(left|center|right)$")) {
            throw new IllegalArgumentException("对齐方式只能是 left, center, right");
        }
    }
}
