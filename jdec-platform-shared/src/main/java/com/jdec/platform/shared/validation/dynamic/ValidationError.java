package com.jdec.platform.shared.validation.dynamic;

/**
 * 单条校验错误。
 *
 * @param path 记录在请求树中的完整路径，例如 {@code nestedModuleData.record[0].children[0].record[1]}
 * @param moduleId 模块 ID，结构级错误可能为 {@code null}
 * @param fieldKey 字段标识，模块级错误可能为 {@code null}
 * @param code 稳定错误码，见 {@link ValidationErrorCodes}
 * @param message 可展示的错误信息，不应包含敏感数据
 */
public record ValidationError(
        String path, String moduleId, String fieldKey, String code, String message) {}
