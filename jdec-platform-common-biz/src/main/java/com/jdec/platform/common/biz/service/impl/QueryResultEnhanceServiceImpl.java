package com.jdec.platform.common.biz.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        String businessTypeCode = findBusinessTypeCodeInHeader(headerRoot, fieldId, columnName);
        return enhanceOptions(rawOptions, columnName, rootModuleId, businessTypeCode);
    }

    private String findBusinessTypeCodeInHeader(
            MenuHeaderColumnResp node, Long fieldId, String columnName) {
        if (node == null) {
            return null;
        }
        if (fieldId != null && fieldId.equals(node.getFieldId())) {
            return node.getBusinessTypeCode();
        }
        if (StringUtils.hasText(columnName) && StringUtils.hasText(node.getDataIndex())) {
            String di = node.getDataIndex().trim().toLowerCase();
            String col = columnName.trim().toLowerCase();
            if (di.equals(col) || di.endsWith("." + col)) {
                return node.getBusinessTypeCode();
            }
        }
        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                String code = findBusinessTypeCodeInHeader(child, fieldId, columnName);
                if (code != null) {
                    return code;
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
