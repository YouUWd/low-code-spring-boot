package com.jdec.platform.shared.validation.dynamic;

/**
 * 动态校验选项。
 *
 * <p>默认值面向“业务系统只定义部分字段规则”的场景，因此不拒绝未定义字段。若模块定义覆盖了完整的可写字段目录， 可使用 {@link #strict()}
 * 打开未知字段拒绝，符合设计文档中的安全规则。
 *
 * @param mode 校验模式
 * @param rejectUnknownFields 是否拒绝模块定义之外的字段
 * @param rejectNotWritableFields 是否拒绝只读字段
 * @param rejectNotApplicableFields 是否拒绝不适用字段
 * @param maxDepth 最大嵌套模块深度
 * @param maxRecords 最大记录数量
 */
public record ValidationOptions(
        ValidationMode mode,
        boolean rejectUnknownFields,
        boolean rejectNotWritableFields,
        boolean rejectNotApplicableFields,
        int maxDepth,
        int maxRecords) {

    public ValidationOptions {
        if (mode == null) {
            mode = ValidationMode.SUBMIT;
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth 必须大于 0");
        }
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords 必须大于 0");
        }
    }

    public static ValidationOptions defaults() {
        return new ValidationOptions(ValidationMode.SUBMIT, false, true, true, 50, 20000);
    }

    /** 严格模式：拒绝未定义字段。要求模块定义覆盖完整的可写字段目录。 */
    public static ValidationOptions strict() {
        return new ValidationOptions(ValidationMode.SUBMIT, true, true, true, 50, 20000);
    }

    public static ValidationOptions forMode(ValidationMode mode) {
        ValidationOptions defaults = defaults();
        return defaults.withMode(mode);
    }

    public ValidationOptions withMode(ValidationMode mode) {
        return new ValidationOptions(
                mode,
                rejectUnknownFields,
                rejectNotWritableFields,
                rejectNotApplicableFields,
                maxDepth,
                maxRecords);
    }

    public ValidationOptions withRejectUnknownFields(boolean rejectUnknownFields) {
        return new ValidationOptions(
                mode,
                rejectUnknownFields,
                rejectNotWritableFields,
                rejectNotApplicableFields,
                maxDepth,
                maxRecords);
    }

    public ValidationOptions withLimits(int maxDepth, int maxRecords) {
        return new ValidationOptions(
                mode,
                rejectUnknownFields,
                rejectNotWritableFields,
                rejectNotApplicableFields,
                maxDepth,
                maxRecords);
    }
}
