package com.jdec.platform.shared.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Map;

/** JSON 值工具：处理以 JSON 文本存储、读取后还原为 Object 的动态业务值。 */
public final class JsonValueUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonValueUtils() {}

    /**
     * 转换为可安全绑定到 SQL 的值。
     *
     * <p>字符串、数字、布尔等标量原样返回；对象、数组等复合值转为 JSON 文本参与比较， 避免把 {@code Map}/{@code Collection} 直接绑定给
     * JDBC（jOOQ 会抛「Type class java.util.ArrayList is not supported」）。
     */
    public static Object toSqlValue(Object value) {
        if (value == null
                || value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map || value instanceof Collection) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                return String.valueOf(value);
            }
        }
        return value;
    }
}
