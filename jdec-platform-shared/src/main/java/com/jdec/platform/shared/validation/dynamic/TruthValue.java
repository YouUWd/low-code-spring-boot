package com.jdec.platform.shared.validation.dynamic;

/**
 * 三态逻辑结果。
 *
 * <p>{@link #UNKNOWN} 表示控制字段缺失、类型错误、引用目标不存在或集合为空等无法确定的情况。不能把 {@code UNKNOWN} 当作 {@code
 * FALSE}，否则依赖字段可能绕过必填校验。
 */
public enum TruthValue {
    TRUE,
    FALSE,
    UNKNOWN
}
