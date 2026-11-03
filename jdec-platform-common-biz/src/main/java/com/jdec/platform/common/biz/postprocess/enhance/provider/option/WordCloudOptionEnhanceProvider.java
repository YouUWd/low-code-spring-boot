package com.jdec.platform.common.biz.postprocess.enhance.provider.option;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.common.biz.postprocess.enhance.context.OptionEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.provider.OptionEnhanceProvider;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 词云配置候选项增强提供者 (word / word_cloud / relationWord)
 *
 * <p>针对词云配置字段的下拉候选项进行增强过滤与 JSON 语义去重： 1. 统一转为 JsonNode 树结构 2. 递归剔除内部结构字段 (id, pid, relation_no,
 * relation_id, word_id) 3. 利用 JsonNode 原生 equals/hashCode（忽略 Key 顺序）进行严格的 JSON 语义去重 4.
 * 转回通用对象结构保持对前端的兼容完整信息
 */
@Component
public class WordCloudOptionEnhanceProvider implements OptionEnhanceProvider {

    private static final Set<String> WORD_CLOUD_REMOVED_FIELDS =
            Set.of("id", "pid", "relation_no", "relation_id", "word_id");

    private final ObjectMapper objectMapper;

    @Autowired
    public WordCloudOptionEnhanceProvider(@Autowired(required = false) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean supports(OptionEnhanceContext ctx) {
        if (ctx == null) {
            return false;
        }
        String code = ctx.getBusinessTypeCode();
        if (isWordCloudType(code)) {
            return true;
        }
        MenuHeaderColumnResp node = ctx.getTargetHeaderNode();
        return node != null
                && (StringUtils.hasText(node.getRelationWord())
                        || isWordCloudType(node.getBusinessTypeCode()));
    }

    @Override
    public List<DataOptionResp> enhance(List<DataOptionResp> rawOptions, OptionEnhanceContext ctx) {
        if (CollectionUtils.isEmpty(rawOptions)) {
            return rawOptions;
        }
        List<DataOptionResp> result = new ArrayList<>();
        Set<JsonNode> seenNodes = new LinkedHashSet<>();

        for (DataOptionResp opt : rawOptions) {
            if (opt == null) {
                continue;
            }
            JsonNode valNode = toJsonNode(opt.getValue());
            JsonNode labelNode = toJsonNode(opt.getLabel());

            // 递归移除指定的元数据字段
            removeWordCloudFields(valNode);
            removeWordCloudFields(labelNode);

            JsonNode targetNode =
                    (valNode != null && !valNode.isNull() && !valNode.isMissingNode())
                            ? valNode
                            : labelNode;
            if (targetNode == null || targetNode.isNull() || targetNode.isMissingNode()) {
                continue;
            }

            // 语义等价去重（ObjectNode.equals 天然忽略 key 顺序）
            if (seenNodes.add(targetNode)) {
                Object finalVal = toObjectValue(targetNode);
                Object finalLabel =
                        (labelNode != null && !labelNode.isNull() && !labelNode.isMissingNode())
                                ? toObjectValue(labelNode)
                                : finalVal;
                result.add(DataOptionResp.builder().label(finalLabel).value(finalVal).build());
            }
        }
        return result;
    }

    private boolean isWordCloudType(String businessTypeCode) {
        return "word".equalsIgnoreCase(businessTypeCode)
                || "word_cloud".equalsIgnoreCase(businessTypeCode)
                || "wordCloud".equalsIgnoreCase(businessTypeCode);
    }

    private JsonNode toJsonNode(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof JsonNode node) {
            return node.deepCopy();
        }
        if (obj instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    return objectMapper.readTree(trimmed);
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
        try {
            return objectMapper.valueToTree(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removeWordCloudFields(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            for (String field : WORD_CLOUD_REMOVED_FIELDS) {
                objectNode.remove(field);
            }
            for (Iterator<JsonNode> it = objectNode.elements(); it.hasNext(); ) {
                removeWordCloudFields(it.next());
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode item : arrayNode) {
                removeWordCloudFields(item);
            }
        }
    }

    private Object toObjectValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, Object.class);
        } catch (Exception e) {
            return node.toString();
        }
    }
}
