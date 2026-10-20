package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 解析后的控制字段值。
 *
 * @param record 字段所在记录
 * @param fieldKey 字段标识
 * @param value 字段值；字段缺失时为 {@code MissingNode}
 * @param invalid 字段是否已校验失败（类型错误、枚举错误等）
 */
public record ResolvedFieldValue(
        RecordNode record, String fieldKey, JsonNode value, boolean invalid) {}
