package com.jdec.platform.common.biz.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.common.biz.constant.IndustryConstants;
import com.jdec.platform.common.biz.service.QueryResultEnhanceService;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 菜单查询结果与候选项数据增强服务实现类 */
@Slf4j
@Service
public class QueryResultEnhanceServiceImpl implements QueryResultEnhanceService {

    private final SysUserApi sysUserApi;
    private final SysStatusApi sysStatusApi;
    private final ObjectMapper objectMapper;

    @Autowired
    public QueryResultEnhanceServiceImpl(
            @Autowired(required = false) SysUserApi sysUserApi,
            @Autowired(required = false) SysStatusApi sysStatusApi,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.sysUserApi = sysUserApi;
        this.sysStatusApi = sysStatusApi;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public QueryResultEnhanceServiceImpl(SysUserApi sysUserApi, SysStatusApi sysStatusApi) {
        this(sysUserApi, sysStatusApi, new ObjectMapper());
    }

    @Override
    public void enhanceDataRows(List<DataRow> records, Long rootModuleId, String primaryTable) {
        enhanceDataRows(records, rootModuleId, primaryTable, null);
    }

    @Override
    public void enhanceDataRows(
            List<DataRow> records,
            Long rootModuleId,
            String primaryTable,
            MenuHeaderColumnResp headerRoot) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        Set<Long> userIds = new HashSet<>();
        Set<Integer> statusValues = new HashSet<>();

        // 阶段 1: 扫描所有数据行收集 userIds, statusValues
        for (DataRow row : records) {
            if (row == null) {
                continue;
            }
            if (primaryTable != null && !primaryTable.isBlank()) {
                Long uid = row.getLong(primaryTable, "created_by");
                if (uid != null) {
                    userIds.add(uid);
                }
                Integer sv = row.getInteger(primaryTable, "business_status");
                if (sv != null) {
                    statusValues.add(sv);
                }
            }
        }

        Map<Long, UserSimpleResp> userMap = queryUserMap(userIds);
        Map<Integer, StatusSimpleResp> statusMap = queryStatusMap(statusValues, rootModuleId);

        // 阶段 2: 填充结构化对象
        for (DataRow row : records) {
            if (row == null) {
                continue;
            }
            if (primaryTable != null && !primaryTable.isBlank()) {
                Long uid = row.getLong(primaryTable, "created_by");
                if (uid != null) {
                    UserSimpleResp userResp = userMap.get(uid);
                    row.putValue(
                            primaryTable,
                            "created_by",
                            userResp != null
                                    ? userResp
                                    : UserSimpleResp.builder().userId(uid).build());
                }
                Integer sv = row.getInteger(primaryTable, "business_status");
                if (sv != null) {
                    StatusSimpleResp statusResp = statusMap.get(sv);
                    row.putValue(
                            primaryTable,
                            "business_status",
                            statusResp != null
                                    ? statusResp
                                    : StatusSimpleResp.builder().statusValue(sv).build());
                }
            }

            // 递归处理自相似子区块
            if (row.getChildren() != null) {
                for (DataBlock childBlock : row.getChildren()) {
                    if (childBlock != null && !CollectionUtils.isEmpty(childBlock.getRecords())) {
                        enhanceDataRows(
                                childBlock.getRecords(), rootModuleId, primaryTable, headerRoot);
                    }
                }
            }
        }
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            String businessTypeCode) {
        if (CollectionUtils.isEmpty(rawOptions)) {
            return rawOptions;
        }

        String normalizedCol =
                columnName != null && columnName.contains(".")
                        ? columnName.substring(columnName.lastIndexOf('.') + 1).trim()
                        : (columnName != null ? columnName.trim() : "");

        if ("created_by".equalsIgnoreCase(normalizedCol)
                || "create_by".equalsIgnoreCase(normalizedCol)
                || "user".equalsIgnoreCase(businessTypeCode)) {
            Set<Long> userIds = new LinkedHashSet<>();
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    userIds.add(n.longValue());
                }
            }
            Map<Long, UserSimpleResp> userMap = queryUserMap(userIds);
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    Long uid = n.longValue();
                    UserSimpleResp finalUser =
                            userMap.getOrDefault(uid, UserSimpleResp.builder().userId(uid).build());
                    opt.setValue(finalUser);
                    opt.setLabel(finalUser);
                }
            }
            return rawOptions;
        }

        if ("business_status".equalsIgnoreCase(normalizedCol)
                || "status".equalsIgnoreCase(businessTypeCode)) {
            Set<Integer> statusValues = new LinkedHashSet<>();
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    statusValues.add(n.intValue());
                }
            }
            Map<Integer, StatusSimpleResp> statusMap = queryStatusMap(statusValues, rootModuleId);
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    Integer sv = n.intValue();
                    StatusSimpleResp finalStatus =
                            statusMap.getOrDefault(
                                    sv, StatusSimpleResp.builder().statusValue(sv).build());
                    opt.setValue(finalStatus);
                    opt.setLabel(finalStatus);
                }
            }
            return rawOptions;
        }

        if ("industry_types".equalsIgnoreCase(normalizedCol)
                || "industryTypes".equalsIgnoreCase(normalizedCol)
                || "industry_type".equalsIgnoreCase(normalizedCol)
                || "industry".equalsIgnoreCase(businessTypeCode)
                || "industry_types".equalsIgnoreCase(businessTypeCode)) {
            return enhanceIndustryOptions(rawOptions);
        }

        if (isWordCloudType(businessTypeCode)) {
            return enhanceWordCloudOptions(rawOptions);
        }

        return rawOptions;
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions, String columnName, Long rootModuleId) {
        return enhanceOptions(rawOptions, columnName, rootModuleId, (String) null);
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            MenuHeaderColumnResp headerRoot,
            Long fieldId) {
        MenuHeaderColumnResp node = findHeaderNode(headerRoot, fieldId, columnName);
        if (node != null
                && (StringUtils.hasText(node.getRelationWord())
                        || isWordCloudType(node.getBusinessTypeCode()))) {
            return enhanceWordCloudOptions(rawOptions);
        }
        String businessTypeCode = node != null ? node.getBusinessTypeCode() : null;
        return enhanceOptions(rawOptions, columnName, rootModuleId, businessTypeCode);
    }

    private boolean isWordCloudType(String businessTypeCode) {
        return "word".equalsIgnoreCase(businessTypeCode)
                || "word_cloud".equalsIgnoreCase(businessTypeCode)
                || "wordCloud".equalsIgnoreCase(businessTypeCode);
    }

    private static final Set<String> WORD_CLOUD_REMOVED_FIELDS =
            Set.of("id", "pid", "relation_no", "relation_id", "word_id");

    /**
     * 针对词云配置字段的下拉候选项进行增强过滤与 JSON 语义去重： 1. 统一转为 JsonNode 树结构 2. 递归剔除内部结构字段 (id, pid, relation_no) 3.
     * 利用 JsonNode 原生 equals/hashCode（忽略 Key 顺序）进行严格的 JSON 语义去重 4. 转回 Map 结构保持对前端的兼容完整信息
     */
    private List<DataOptionResp> enhanceWordCloudOptions(List<DataOptionResp> rawOptions) {
        if (CollectionUtils.isEmpty(rawOptions)) {
            return rawOptions;
        }
        List<DataOptionResp> result = new ArrayList<>();
        // 利用 LinkedHashSet + JsonNode 原生 equals/hashCode 实现忽略 Key 声明顺序的语义去重
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
                // 转为 Map/List 结构，保持过滤后的完整信息
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

    /** 统一将 Object / String / Map 转换为 JsonNode */
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

    /** 递归剔除 id, pid, relation_no（与 WordApiController / DynamicQueryService 规范统一） */
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

    /** 将 JsonNode 转换回通用 Map/List/标量对象 */
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

    private MenuHeaderColumnResp findHeaderNode(
            MenuHeaderColumnResp node, Long fieldId, String columnName) {
        if (node == null) {
            return null;
        }
        if (fieldId != null && fieldId.equals(node.getFieldId())) {
            return node;
        }
        if (StringUtils.hasText(columnName) && StringUtils.hasText(node.getDataIndex())) {
            String di = node.getDataIndex().trim().toLowerCase(Locale.ROOT);
            String col = columnName.trim().toLowerCase(Locale.ROOT);
            if (di.equals(col) || di.endsWith("." + col)) {
                return node;
            }
        }
        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                MenuHeaderColumnResp target = findHeaderNode(child, fieldId, columnName);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    private Map<Long, UserSimpleResp> queryUserMap(Set<Long> userIds) {
        Map<Long, UserSimpleResp> userMap = new HashMap<>();
        if (CollectionUtils.isEmpty(userIds)) {
            return userMap;
        }
        try {
            QueryUsersByIdsReq queryReq = new QueryUsersByIdsReq();
            queryReq.setUserIds(new ArrayList<>(userIds));
            List<SysUserPageResp> userList = sysUserApi.getUsersByUserIds(queryReq);
            if (userList != null) {
                for (SysUserPageResp u : userList) {
                    if (u == null) {
                        continue;
                    }
                    UserSimpleResp resp =
                            UserSimpleResp.builder()
                                    .userId(u.getUserId())
                                    .userName(u.getUserName())
                                    .userAvatar(u.getUserAvatar())
                                    .workNumber(u.getWorkNumber())
                                    .build();
                    if (u.getUserId() != null) {
                        userMap.put(u.getUserId(), resp);
                    }
                    if (u.getId() != null) {
                        userMap.putIfAbsent(u.getId(), resp);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("批量查询用户信息失败: userIds={}, err={}", userIds, ex.getMessage());
        }
        return userMap;
    }

    private Map<Integer, StatusSimpleResp> queryStatusMap(
            Set<Integer> statusValues, Long rootModuleId) {
        Map<Integer, StatusSimpleResp> statusMap = new HashMap<>();
        if (CollectionUtils.isEmpty(statusValues) || rootModuleId == null) {
            return statusMap;
        }
        for (Integer sv : statusValues) {
            try {
                ModuleApprovalChainConfigResp.StatusNode node =
                        sysStatusApi.getStatusByValue(sv, rootModuleId);
                if (node != null) {
                    statusMap.put(
                            sv,
                            StatusSimpleResp.builder()
                                    .title(node.getTitle())
                                    .statusValue(node.getStatusValue())
                                    .statusBackground(node.getStatusBackground())
                                    .statusFontColor(node.getStatusFontColor())
                                    .build());
                }
            } catch (Exception ex) {
                log.warn(
                        "获取模块状态信息失败: moduleId={}, statusValue={}, err={}",
                        rootModuleId,
                        sv,
                        ex.getMessage());
            }
        }
        return statusMap;
    }

    private List<DataOptionResp> enhanceIndustryOptions(List<DataOptionResp> rawOptions) {
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
