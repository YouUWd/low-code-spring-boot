package com.jdec.platform.externalobject.biz.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 外部对象字段值处理工具。
 *
 * <p>来自词云的字段在请求中是一段 JSON（对象/数组，可能是 JSON 字符串），真正的值保存在 {@code
 * alias_value_title}；本工具统一把这类值转换为可比较的文本列表。
 */
public final class ExternalObjectWordValues {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 词云真实值键名 */
    private static final String TITLE_KEY = "alias_value_title";

    /** 词云描述键名（旧数据没有 alias_value_title 时兜底） */
    private static final String DESCRIBE_KEY = "word_describe";

    private ExternalObjectWordValues() {}

    /** 空值语义：null、空白、{@code []}、{@code {}}、字符串 null。 */
    public static boolean isBlank(String raw) {
        if (raw == null) {
            return true;
        }
        String text = raw.trim();
        return text.isEmpty()
                || "[]".equals(text)
                || "{}".equals(text)
                || "null".equalsIgnoreCase(text);
    }

    /** 任意请求值转为文本；非标量转为 JSON 字符串。 */
    public static String toText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence text) {
            return text.toString().trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** 提取字段真实值：词云 JSON 取 {@code alias_value_title}，普通文本按原值返回（去重且保持顺序）。 */
    public static List<String> titles(String raw) {
        if (raw == null) {
            return List.of();
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        JsonNode node = parse(text);
        if (node == null) {
            result.add(text);
        } else {
            collectTitles(node, result);
        }
        return new ArrayList<>(result);
    }

    /** 字段值是否包含指定关键字（兼容词云、数组、JSON 字符串与普通文本）。 */
    public static boolean containsTitle(String raw, String keyword) {
        if (keyword == null) {
            return false;
        }
        for (String title : titles(raw)) {
            if (title.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 字段值是否包含任意关键字。 */
    public static boolean containsAnyTitle(String raw, String... keywords) {
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (containsTitle(raw, keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 人员类型是否为员工（兼容 EMPLOYEE/employee/员工）。 */
    public static boolean isEmployeeType(String type) {
        if (isBlank(type)) {
            return false;
        }
        for (String title : titles(type)) {
            if (title.contains("员工") || title.toUpperCase(Locale.ROOT).contains("EMPLOYEE")) {
                return true;
            }
        }
        return false;
    }

    /** 对象性质是否为个人。 */
    public static boolean isIndividualNature(String nature) {
        return containsTitle(nature, "个人");
    }

    /** 对象性质是否为机关。 */
    public static boolean isAgencyNature(String nature) {
        return containsTitle(nature, "机关");
    }

    /** 去除首尾空白；null 返回空串。 */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 去除全部空白；用于手机号、证件号等允许输入空格的字段。 */
    public static String compact(String value) {
        return trim(value).replaceAll("\\s+", "");
    }

    private static JsonNode parse(String text) {
        boolean jsonLike =
                (text.startsWith("{") && text.endsWith("}"))
                        || (text.startsWith("[") && text.endsWith("]"))
                        || (text.startsWith("\"") && text.endsWith("\""));
        if (!jsonLike) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static void collectTitles(JsonNode node, Set<String> result) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return;
            }
            JsonNode nested = parse(text);
            if (nested != null) {
                collectTitles(nested, result);
            } else {
                result.add(text);
            }
            return;
        }
        if (node.isObject()) {
            JsonNode title = node.get(TITLE_KEY);
            if (title != null && title.isTextual() && !title.asText().isBlank()) {
                result.add(title.asText().trim());
                return;
            }
            JsonNode describe = node.get(DESCRIBE_KEY);
            if (describe != null && describe.isTextual() && !describe.asText().isBlank()) {
                result.add(describe.asText().trim());
                return;
            }
            for (JsonNode child : node) {
                collectTitles(child, result);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTitles(child, result);
            }
        }
    }
}
