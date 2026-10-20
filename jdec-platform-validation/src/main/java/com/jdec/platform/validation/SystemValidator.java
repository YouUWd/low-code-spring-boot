package com.jdec.platform.validation;

import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import java.util.List;

/**
 * 业务系统校验器（策略接口）。
 *
 * <p>每个业务系统实现一个本接口并声明 {@link #key()}，启动时由 {@link SystemValidatorRegistry} 收集注册；调用方通过 {@link
 * SystemValidationService} 按「项目编码 + 模块标识 + 主体ID」选择校验器。
 */
public interface SystemValidator {

    /** 注册 key：项目编码 + 模块标识 + 主体ID（subjectId 为 null 表示通配所有主体）。 */
    SystemValidatorKey key();

    /** 执行校验。 */
    ValidationResult validate(ValidationRequest request);

    /**
     * 提取「必填为空」字段的中文名称，按错误顺序去重。
     *
     * <p>根模块字段直接返回字段中文名；子级字段拼接模块层级，例如 {@code 人员构成 - 手机号码}。默认不提供中文名称。
     */
    default List<String> missingRequiredFields(ValidationResult result) {
        return List.of();
    }
}
