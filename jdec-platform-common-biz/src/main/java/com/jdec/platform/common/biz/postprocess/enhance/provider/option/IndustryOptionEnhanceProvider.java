package com.jdec.platform.common.biz.postprocess.enhance.provider.option;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.biz.constant.IndustryConstants;
import com.jdec.platform.common.biz.postprocess.enhance.context.OptionEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.provider.OptionEnhanceProvider;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** 行业类型树状候选项过滤增强提供者 (industry_types / industry) */
@Component
public class IndustryOptionEnhanceProvider implements OptionEnhanceProvider {

    private final ObjectMapper objectMapper;

    @Autowired
    public IndustryOptionEnhanceProvider(@Autowired(required = false) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean supports(OptionEnhanceContext ctx) {
        if (ctx == null) {
            return false;
        }
        String col = ctx.getNormalizedColumn();
        String code = ctx.getBusinessTypeCode();
        return "industry_types".equalsIgnoreCase(col)
                || "industryTypes".equalsIgnoreCase(col)
                || "industry_type".equalsIgnoreCase(col)
                || "industry".equalsIgnoreCase(code)
                || "industry_types".equalsIgnoreCase(code);
    }

    @Override
    public List<DataOptionResp> enhance(List<DataOptionResp> rawOptions, OptionEnhanceContext ctx) {
        if (CollectionUtils.isEmpty(rawOptions)) {
            return rawOptions;
        }
        Set<String> keepSet = extractKeepValues(rawOptions);
        if (keepSet.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> tree = IndustryConstants.INDUSTRY_TREE;
        List<Map<String, Object>> filteredTree = filterIndustryTree(tree, keepSet);
        if (filteredTree.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(DataOptionResp.builder().label(filteredTree).value(filteredTree).build());
    }

    private Set<String> extractKeepValues(List<DataOptionResp> rawOptions) {
        Set<String> keepSet = new LinkedHashSet<>();
        for (DataOptionResp opt : rawOptions) {
            if (opt == null) {
                continue;
            }
            extractKeepValue(opt.getValue(), keepSet);
            extractKeepValue(opt.getLabel(), keepSet);
        }
        return keepSet;
    }

    private void extractKeepValue(Object obj, Set<String> keepSet) {
        if (obj == null) {
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            Object val = map.get("value");
            if (val == null) {
                val = map.get("dict_value");
            }
            if (val != null) {
                keepSet.add(val.toString().trim());
            }
        } else if (obj instanceof String str) {
            String s = str.trim();
            if (s.startsWith("{") && s.endsWith("}")) {
                try {
                    Map<?, ?> map = objectMapper.readValue(s, Map.class);
                    extractKeepValue(map, keepSet);
                } catch (Exception ignored) {
                    keepSet.add(s);
                }
            } else if (!s.isEmpty()) {
                keepSet.add(s);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filterIndustryTree(
            List<Map<String, Object>> nodes, Set<String> keepSet) {
        if (CollectionUtils.isEmpty(nodes) || CollectionUtils.isEmpty(keepSet)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (node == null) {
                continue;
            }

            List<Map<String, Object>> childrenList = null;
            Object rawChildren = node.get("childrenList");
            if (rawChildren instanceof List<?> rawList) {
                childrenList = (List<Map<String, Object>>) (List<?>) rawList;
            }

            List<Map<String, Object>> children =
                    childrenList != null
                            ? filterIndustryTree(childrenList, keepSet)
                            : Collections.emptyList();

            Object valueObj = node.get("value");
            String val = valueObj != null ? valueObj.toString().trim() : null;
            boolean selfMatched = val != null && keepSet.contains(val);

            if (selfMatched || !children.isEmpty()) {
                Map<String, Object> newNode = new LinkedHashMap<>(node);
                if (childrenList != null) {
                    newNode.put("childrenList", children);
                }
                result.add(newNode);
            }
        }
        return result;
    }
}
