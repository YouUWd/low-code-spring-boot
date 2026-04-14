package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 用户字段配置请求
 *
 * <p>用于配置用户可见的字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserFieldConfigReq extends BaseRequest {

    /**
     * 字段编码
     */
    @NotBlank(message = "字段编码不能为空")
    private String fieldCode;

    /**
     * 是否可见
     */
    private Boolean visible;

    /**
     * 展示顺序
     */
    private Integer sortOrder;

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
    }
}
