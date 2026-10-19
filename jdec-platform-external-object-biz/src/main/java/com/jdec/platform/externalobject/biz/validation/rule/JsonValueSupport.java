package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.externalobject.biz.validation.ExternalObjectModules;
import com.jdec.platform.shared.validation.dynamic.ConditionContext;
import com.jdec.platform.shared.validation.dynamic.FieldRef;
import com.jdec.platform.shared.validation.dynamic.FieldRefResolver;
import com.jdec.platform.shared.validation.dynamic.ModuleDefinitionRegistry;
import com.jdec.platform.shared.validation.dynamic.RecordNode;
import com.jdec.platform.shared.validation.dynamic.RequestTree;
import com.jdec.platform.shared.validation.dynamic.ResolvedFieldValue;
import com.jdec.platform.shared.validation.dynamic.ResolvedFieldValues;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.util.List;

/** 外部对象表单校验的 JSON 值处理工具。 */
public final class JsonValueSupport {

    public static final String INFO_NATURE = "info.nature";
    public static final String INFO_GENDER = "info.gender";
    public static final String BASIC_INFO_USCC = "basic_info.uscc";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String[][] USCC_PREFIXES = {
        {"个体工商户", "92"},
        {"律师执业机构", "31"},
        {"村民委员会", "54"},
        {"居民委员会", "55"},
        {"事业单位", "12"},
        {"社会团体", "51"},
        {"机关", "11"},
        {"政府", "11"},
        {"企业", "91"}
    };

    private JsonValueSupport() {}

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
     * <p>兼容三种形态：真实 JSON 节点、JSON 字符串、普通字符串。对象性质等字段在请求中既可能是数组，也可能是 JSON 字符串。
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

    /** 读取 basic_info 记录所属 info 记录的 nature。 */
    public static JsonNode resolveParentNature(ValidationContext context) {
        return resolveInfoNature(
                context.tree(), context.current(), context.state(), context.registry());
    }

    /** 读取 basic_info 记录所属 info 记录的 nature。 */
    public static JsonNode resolveParentNature(ConditionContext context) {
        return resolveInfoNature(
                context.tree(), context.current(), context.state(), context.registry());
    }

    /** 读取条件上下文的 nature：info 记录取当前行，其余模块向上查找最近的 info 记录。 */
    public static JsonNode resolveNature(ConditionContext context) {
        if (ExternalObjectModules.INFO.equals(context.current().moduleId())) {
            return read(context.current().row(), INFO_NATURE);
        }
        return resolveParentNature(context);
    }

    /** 读取 basic_info 记录所属 info 记录的 gender。 */
    public static JsonNode resolveParentGender(ValidationContext context) {
        for (FieldRef ref :
                List.of(
                        FieldRef.parent(ExternalObjectModules.INFO, INFO_GENDER),
                        FieldRef.nearestAncestor(ExternalObjectModules.INFO, INFO_GENDER),
                        FieldRef.moduleAny(ExternalObjectModules.INFO, INFO_GENDER))) {
            JsonNode value =
                    firstValue(
                            FieldRefResolver.resolve(
                                    ref,
                                    context.tree(),
                                    context.current(),
                                    context.state(),
                                    context.registry()));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static JsonNode resolveInfoNature(
            RequestTree tree,
            RecordNode current,
            ValidationState state,
            ModuleDefinitionRegistry registry) {
        for (FieldRef ref :
                List.of(
                        FieldRef.parent(ExternalObjectModules.INFO, INFO_NATURE),
                        FieldRef.nearestAncestor(ExternalObjectModules.INFO, INFO_NATURE),
                        FieldRef.moduleAny(ExternalObjectModules.INFO, INFO_NATURE))) {
            JsonNode value =
                    firstValue(FieldRefResolver.resolve(ref, tree, current, state, registry));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** 读取当前 info 记录的 nature。 */
    public static JsonNode resolveCurrentNature(ValidationContext context) {
        JsonNode value = context.currentField(INFO_NATURE);
        return value == null || value.isMissingNode() ? null : value;
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

    /** 政府单位、事业单位的 uscc 非必填。 */
    public static boolean isUsccExempt(JsonNode nature) {
        return containsText(nature, "机关")
                || containsText(nature, "政府")
                || containsText(nature, "事业单位");
    }

    public static boolean isIndividual(JsonNode nature) {
        return containsText(nature, "个人");
    }

    /** 按对象性质返回统一社会信用代码前两位；未知性质返回 null。 */
    public static String usccPrefix(JsonNode nature) {
        for (String[] item : USCC_PREFIXES) {
            if (containsText(nature, item[0])) {
                return item[1];
            }
        }
        return null;
    }

    private static JsonNode firstValue(ResolvedFieldValues resolved) {
        if (resolved == null || !resolved.targetFound()) {
            return null;
        }
        for (ResolvedFieldValue value : resolved.values()) {
            JsonNode node = value.value();
            if (node != null && !node.isMissingNode()) {
                return node;
            }
        }
        return null;
    }
}
