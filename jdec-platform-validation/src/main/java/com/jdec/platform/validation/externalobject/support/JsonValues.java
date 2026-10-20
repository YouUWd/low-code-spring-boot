package com.jdec.platform.validation.externalobject.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** 通用 JSON 值处理工具，供各业务系统的校验规则共用。 */
public final class JsonValues {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonValues() {}

    /** 必填语义：缺失、null、空白字符串、空数组、空对象均视为空。 */
    public static boolean isBlank(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return true;
        }
        if (value.isTextual()) {
            String text = value.asText().trim();
            return text.isEmpty() || "[]".equals(text) || "{}".equals(text);
        }
        if (value.isArray() || value.isObject()) {
            return value.isEmpty();
        }
        return false;
    }

    /**
     * 递归判断 JSON 值中是否包含指定文本。
     *
     * <p>兼容三种形态：真实 JSON 节点、JSON 字符串、普通字符串。
     */
    public static boolean containsText(JsonNode node, String text) {
        if (node == null || node.isMissingNode() || node.isNull() || text == null) {
            return false;
        }
        if (node.isTextual()) {
            String raw = node.asText();
            if (raw.contains(text)) {
                return true;
            }
            String trimmed = raw.trim();
            if ((trimmed.startsWith("[") && trimmed.endsWith("]"))
                    || (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
                try {
                    return containsText(OBJECT_MAPPER.readTree(trimmed), text);
                } catch (Exception ignored) {
                    return false;
                }
            }
            return false;
        }
        if (node.isValueNode()) {
            return node.asText().contains(text);
        }
        for (JsonNode child : node) {
            if (containsText(child, text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取 JSON 值中的文本内容。
     *
     * <p>兼容普通文本、JSON 字符串、字符串数组，以及词云对象数组（优先取 {@code word_describe}）。
     */
    public static List<String> extractTexts(JsonNode node) {
        List<String> texts = new ArrayList<>();
        collectTexts(node, texts);
        return texts;
    }

    private static void collectTexts(JsonNode node, List<String> texts) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String raw = node.asText().trim();
            if (raw.isEmpty()) {
                return;
            }
            if ((raw.startsWith("[") && raw.endsWith("]"))
                    || (raw.startsWith("{") && raw.endsWith("}"))) {
                try {
                    collectTexts(OBJECT_MAPPER.readTree(raw), texts);
                    return;
                } catch (Exception ignored) {
                    // 普通文本恰以括号开头/结尾时按原值处理。
                }
            }
            texts.add(raw);
            return;
        }
        if (node.isObject()) {
            JsonNode describe = node.get("word_describe");
            if (describe != null && describe.isTextual() && !describe.asText().isBlank()) {
                texts.add(describe.asText().trim());
                return;
            }
        }
        if (node.isValueNode()) {
            return;
        }
        for (JsonNode child : node) {
            collectTexts(child, texts);
        }
    }

    /** 判断值节点文本是否包含任意一个关键字。 */
    public static boolean containsAnyText(JsonNode value, String... texts) {
        if (texts == null) {
            return false;
        }
        for (String text : texts) {
            if (containsText(value, text)) {
                return true;
            }
        }
        return false;
    }

    /** 读取行内的字段值，行或字段不存在时返回 null。 */
    public static JsonNode read(JsonNode row, String fieldKey) {
        if (row == null || row.isNull()) {
            return null;
        }
        JsonNode value = row.get(fieldKey);
        return value == null || value.isMissingNode() ? null : value;
    }

    /** 值节点的文本内容，去除首尾空白；非值节点返回空字符串。 */
    public static String text(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull() || !value.isValueNode()) {
            return "";
        }
        return value.asText().trim();
    }

    /** 去除全部空白后的文本；用于手机号、账号等允许前端输入空格的字段。 */
    public static String compactText(JsonNode value) {
        return text(value).replaceAll("\\s+", "");
    }
}
