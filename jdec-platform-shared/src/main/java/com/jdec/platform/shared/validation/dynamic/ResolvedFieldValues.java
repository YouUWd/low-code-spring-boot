package com.jdec.platform.shared.validation.dynamic;

import java.util.List;

/**
 * 字段解析结果。
 *
 * @param values 解析到的字段值；{@code targetFound} 为 false 时为空
 * @param targetFound 是否找到引用目标记录
 * @param reason 未找到时的原因
 */
public record ResolvedFieldValues(
        List<ResolvedFieldValue> values, boolean targetFound, String reason) {

    public ResolvedFieldValues {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static ResolvedFieldValues found(List<ResolvedFieldValue> values) {
        return new ResolvedFieldValues(values, true, null);
    }

    public static ResolvedFieldValues missing(String reason) {
        return new ResolvedFieldValues(List.of(), false, reason);
    }
}
