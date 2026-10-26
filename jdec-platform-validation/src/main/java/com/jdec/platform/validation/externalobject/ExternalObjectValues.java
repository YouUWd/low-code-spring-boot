package com.jdec.platform.validation.externalobject;

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
 * <p>来自词云的字段在库中是一段 JSON（对象/数组，可能是 JSON 字符串），真正的值保存在 {@code
 * alias_value_title}；本工具统一把这类值转换为可比较的文本列表，并提供对象性质、人员类型的判定。
 */
public final class ExternalObjectValues {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 词云真实值键名 */
    private static final String TITLE_KEY = "alias_value_title";

    /** 词云描述键名（旧数据没有 alias_value_title 时兜底） */
    private static final String DESCRIBE_KEY = "word_describe";

    private ExternalObjectValues() {}

    /** 空值语义：null、空白、{@code []}、{@code {}}、字符串 null、JSON null。 */
    public static boolean isBlankJson(String raw) {
        String text = trim(raw);
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return true;
        }
        JsonNode node = parse(text);
        if (node == null) {
            return false;
        }
        if (node.isNull() || node.isMissingNode()) {
            return true;
        }
        if (node.isArray() || node.isObject()) {
            return node.isEmpty();
        }
        return node.isTextual() && node.asText().isBlank();
    }

    /** 普通文本判空。 */
    public static boolean isBlank(String value) {
        return trim(value).isEmpty();
    }

    /** 去除首尾空白；null 返回空串。 */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 去除全部空白；用于手机号、证件号等允许输入空格的字段。 */
    public static String compact(String value) {
        return trim(value).replaceAll("\\s+", "");
    }

    /** 去除全部空白并转大写；用于证件号、统一社会信用代码。 */
    public static String compactUpper(String value) {
        return compact(value).toUpperCase(Locale.ROOT);
    }

    /** 提取字段真实值：词云 JSON 取 {@code alias_value_title}，普通文本按原值返回（去重且保持顺序）。 */
    public static List<String> titles(String raw) {
        String text = trim(raw);
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

    /** 字段值的首个真实值；无值时返回空串。 */
    public static String firstTitle(String raw) {
        List<String> values = titles(raw);
        return values.isEmpty() ? "" : values.get(0);
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

    /** 对象性质是否为个人。 */
    public static boolean isIndividualNature(String nature) {
        return containsTitle(nature, "个人");
    }

    /** 对象性质是否为机关。 */
    public static boolean isAgencyNature(String nature) {
        return containsTitle(nature, "机关");
    }

    /** 对象性质是否为企业。 */
    public static boolean isEnterpriseNature(String nature) {
        return containsTitle(nature, "企业");
    }

    /** 对象性质是否为个体工商户。 */
    public static boolean isIndividualBusinessNature(String nature) {
        return containsTitle(nature, "个体工商户");
    }

    /** 对象性质是否为事业单位。 */
    public static boolean isInstitutionNature(String nature) {
        return containsTitle(nature, "事业单位");
    }

    /** 对象性质是否为社会团体。 */
    public static boolean isSocialGroupNature(String nature) {
        return containsTitle(nature, "社会团体");
    }

    /** 对象性质是否为律师执业机构。 */
    public static boolean isLawyerNature(String nature) {
        return containsTitle(nature, "律师");
    }

    /** 对象性质是否为村民委员会。 */
    public static boolean isVillageCommitteeNature(String nature) {
        return containsTitle(nature, "村民委员会");
    }

    /** 人员类型是否为部门（兼容 DEPT/department/部门）。 */
    public static boolean isDepartmentType(String type) {
        if (isBlank(type)) {
            return false;
        }
        for (String title : titles(type)) {
            String upper = title.toUpperCase(Locale.ROOT);
            if (title.contains("部门") || upper.contains("DEPT") || upper.contains("DEPARTMENT")) {
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
