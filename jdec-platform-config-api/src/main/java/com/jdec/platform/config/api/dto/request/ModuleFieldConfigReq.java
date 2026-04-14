package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 模块字段配置请求
 *
 * <p>用于配置模块的字段显示属性
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ModuleFieldConfigReq extends BaseRequest {

    /**
     * 字段编码
     */
    @NotBlank(message = "字段编码不能为空")
    private String fieldCode;

    /**
     * 字段名称
     */
    @NotBlank(message = "字段名称不能为空")
    private String fieldName;

    /**
     * 字段类型
     */
    private String fieldType;

    /**
     * 是否可见
     */
    @NotNull(message = "可见性不能为空")
    private Boolean visible;

    /**
     * 展示顺序
     */
    @NotNull(message = "展示顺序不能为空")
    @Min(value = 0, message = "展示顺序必须大于等于 0")
    private Integer sortOrder;

    /**
     * 字段宽度
     */
    @Min(value = 1, message = "字段宽度必须大于 0")
    private Integer width;

    /**
     * 对齐方式
     */
    @Pattern(regexp = "^(left|center|right)$", message = "对齐方式只能是 left, center, right")
    private String align;

    /**
     * 验证请求对象
     *
     * @throws IllegalArgumentException 验证失败时抛出
     */
    @Override
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
