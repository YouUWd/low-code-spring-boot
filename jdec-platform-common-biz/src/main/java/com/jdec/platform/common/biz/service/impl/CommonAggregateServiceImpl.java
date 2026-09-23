package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.biz.dto.request.MenuQueryReq;
import com.jdec.platform.common.biz.dto.response.MenuDataResp;
import com.jdec.platform.common.biz.dto.response.MenuDetailResp;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.UserMenuHeaderApi;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.ApprovalRowItem;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DataRecordItem;
import com.jdec.platform.data.api.dto.response.FieldMetaDTO;
import com.jdec.platform.data.api.dto.response.ModuleDataNode;
import com.jdec.platform.data.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.data.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 公共业务聚合服务实现 (基于菜单的数据隔离、动态列表与详情聚合) */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonAggregateServiceImpl implements CommonAggregateService {

    private final UserMenuHeaderApi userMenuHeaderApi;
    private final ConfigApi configApi;
    private final DataApi dataApi;
    private final IApprovalEngine approvalEngine;

    @Override
    public MenuDataResp queryByMenu(Long menuId, MenuQueryReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        Long userId = UserContext.getUserId();
        Long roleId = UserContext.getRoleId();

        // 1. 获取用户在该菜单下的个性化表头展示配置 (用于提取已勾选字段与多级表头)
        MenuHeaderDisplayResp headerDisplay =
                userMenuHeaderApi.getUserMenuHeaderDisplay(userId, menuId, roleId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }

        MenuHeaderColumnResp headerRoot = headerDisplay.getHeader();
        Long rootModuleId = headerRoot.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 对应的表头未关联有效的根模块 ID");
        }

        List<Long> checkedFieldIds = new ArrayList<>();
        collectCheckedFieldIds(headerRoot, checkedFieldIds);

        // 2. 解析根模块核心字段 ID (id, created_by, business_status)
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId);

        // 3. 构建底层通用 QueryReq 并组装通用过滤/排序/分页
        int pageNo = (req != null && req.getPageNo() != null) ? req.getPageNo() : 1;
        int pageSize = (req != null && req.getPageSize() != null) ? req.getPageSize() : 20;

        QueryReq.QueryReqBuilder queryReqBuilder =
                QueryReq.builder()
                        .moduleId(rootModuleId)
                        .fields(checkedFieldIds)
                        .pageNo(pageNo)
                        .pageSize(pageSize)
                        .sorts(req != null ? req.getSorts() : null);

        List<FilterItem> finalFilters = new ArrayList<>();
        if (req != null && req.getFilters() != null && !req.getFilters().isEmpty()) {
            for (var flatFilter : req.getFilters()) {
                if (flatFilter != null && flatFilter.getFieldId() != null) {
                    finalFilters.add(
                            FilterItem.builder()
                                    .fieldId(flatFilter.getFieldId())
                                    .operator(
                                            flatFilter.getOperator() != null
                                                    ? flatFilter.getOperator()
                                                    : "EQ")
                                    .value(flatFilter.getValue())
                                    .build());
                }
            }
        }

        // 4. 审批流过滤注入 (1-待办, 2-已办, 3-全部)
        Integer approvalType = req != null ? req.getApprovalType() : null;
        ApprovalButtonsGroup approvalGroup = queryApprovalButtons(rootModuleId, approvalType);

        Map<Long, List<Map<String, Object>>> approvalBtnMap = approvalGroup.relevantBtns();
        Map<Long, List<Map<String, Object>>> allBtnsMap = approvalGroup.allBtns();

        ApprovalFilterResult approvalFilterResult =
                buildApprovalFilters(approvalType, rootModuleId, rootFieldIds, approvalBtnMap);
        if (approvalFilterResult.shortCircuitEmpty()) {
            // 短路直接返回空数据
            ModuleDataNode emptyNode =
                    ModuleDataNode.builder()
                            .moduleId(rootModuleId)
                            .menuId(menuId)
                            .record(Collections.emptyList())
                            .build();

            return assembleMenuDataResp(
                    headerRoot, menuId, rootModuleId, roleId, emptyNode, allBtnsMap);
        }

        if (!approvalFilterResult.filters().isEmpty()) {
            finalFilters.addAll(approvalFilterResult.filters());
        }
        queryReqBuilder.filters(finalFilters);

        // 5. 调用底层低代码数据引擎进行分页查询
        DataPage<Map<String, Object>> rawDataPage = dataApi.query(queryReqBuilder.build());

        // 6. 将 Map 数据集结构转换为自相似同构树节点 ModuleDataNode
        ModuleDataNode rootNode =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .record(new ArrayList<>())
                        .build();

        if (rawDataPage != null && rawDataPage.getRecords() != null) {
            String rootModKey = String.valueOf(rootModuleId);
            for (Map<String, Object> rawRecord : rawDataPage.getRecords()) {
                if (rawRecord == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rootRowData =
                        rawRecord.containsKey(rootModKey)
                                        && rawRecord.get(rootModKey) instanceof Map<?, ?>
                                ? (Map<String, Object>) rawRecord.get(rootModKey)
                                : rawRecord;
                rootNode.getRecord().add(buildDataRecordItem(rootModuleId, menuId, rootRowData));
            }
        }

        // 7. 组装并返回复合响应
        return assembleMenuDataResp(headerRoot, menuId, rootModuleId, roleId, rootNode, allBtnsMap);
    }

    @Override
    public MenuDetailResp getDetailByMenu(Long menuId, Long id) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (id == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }

        Long userId = UserContext.getUserId();
        Long roleId = UserContext.getRoleId();

        // 1. 获取表头展示配置提取 rootModuleId
        MenuHeaderDisplayResp headerDisplay =
                userMenuHeaderApi.getUserMenuHeaderDisplay(userId, menuId, roleId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }

        MenuHeaderColumnResp headerRoot = headerDisplay.getHeader();
        Long rootModuleId = headerRoot.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 对应的表头未关联有效的根模块 ID");
        }

        List<Long> checkedFieldIds = new ArrayList<>();
        collectCheckedFieldIds(headerRoot, checkedFieldIds);

        // 2. 调用底层低代码引擎获取完整单据详情
        DataDetail<Map<String, Object>> dataDetail =
                dataApi.getDetail(rootModuleId, id, checkedFieldIds);

        // 3. 将 Map 数据转换为 ModuleDataNode 树
        ModuleDataNode dataTree =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .record(new ArrayList<>())
                        .build();

        if (dataDetail != null && dataDetail.getData() != null) {
            String rootModKey = String.valueOf(rootModuleId);
            Map<String, Object> rawData = dataDetail.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> rootRowData =
                    rawData.containsKey(rootModKey) && rawData.get(rootModKey) instanceof Map<?, ?>
                            ? (Map<String, Object>) rawData.get(rootModKey)
                            : rawData;
            dataTree.getRecord().add(buildDataRecordItem(rootModuleId, menuId, rootRowData));
        }

        // 4. 构建模块元数据配置列表 (ModuleMetaDTO)
        List<ModuleMetaDTO> metaList = buildModuleMetaList(rootModuleId, menuId, roleId);

        // 5. 组装响应结构
        return MenuDetailResp.builder().meta(metaList).data(dataTree).build();
    }

    private void collectCheckedFieldIds(MenuHeaderColumnResp node, List<Long> result) {
        if (node == null) {
            return;
        }
        if (node.getFieldId() != null) {
            result.add(node.getFieldId());
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                collectCheckedFieldIds(child, result);
            }
        }
    }

    private DataRecordItem buildDataRecordItem(
            Long moduleId, Long menuId, Map<String, Object> rowMap) {
        if (rowMap == null) {
            return DataRecordItem.builder().row(Collections.emptyMap()).build();
        }

        Map<String, Object> flatRow = new LinkedHashMap<>();
        List<ModuleDataNode> childrenNodes = new ArrayList<>();

        for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 1. 标准 children 树解析
            if ("children".equals(key) && value instanceof List<?> childrenList) {
                for (Object childObj : childrenList) {
                    if (childObj instanceof Map<?, ?> childMap) {
                        Long subModId = null;
                        Object modIdObj = childMap.get("moduleId");
                        if (modIdObj instanceof Number num) {
                            subModId = num.longValue();
                        } else if (modIdObj != null) {
                            try {
                                subModId = Long.valueOf(modIdObj.toString());
                            } catch (Exception ignored) {
                            }
                        }

                        List<DataRecordItem> childItems = new ArrayList<>();
                        Object recordsObj = childMap.get("records");
                        if (recordsObj instanceof List<?> recList) {
                            for (Object recItem : recList) {
                                if (recItem instanceof Map<?, ?> castedMap) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> mapItem = (Map<String, Object>) castedMap;
                                    childItems.add(buildDataRecordItem(subModId, null, mapItem));
                                }
                            }
                        } else if (recordsObj instanceof Map<?, ?> singleMap) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mapItem = (Map<String, Object>) singleMap;
                            childItems.add(buildDataRecordItem(subModId, null, mapItem));
                        }

                        ModuleDataNode childNode =
                                ModuleDataNode.builder()
                                        .moduleId(subModId)
                                        .record(childItems)
                                        .build();
                        childrenNodes.add(childNode);
                    }
                }
            } else if (value instanceof Map<?, ?> subMap) {
                // 判断是否为历史数字 key 子模块或物理表空间 map (如 key=info)
                boolean isModuleIdKey = false;
                Long subModId = null;
                try {
                    subModId = Long.valueOf(key);
                    isModuleIdKey = true;
                } catch (Exception ignored) {
                }

                if (isModuleIdKey) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> castedSubMap = (Map<String, Object>) subMap;
                    DataRecordItem childItem = buildDataRecordItem(subModId, null, castedSubMap);
                    ModuleDataNode childNode =
                            ModuleDataNode.builder()
                                    .moduleId(subModId)
                                    .record(Collections.singletonList(childItem))
                                    .build();
                    childrenNodes.add(childNode);
                } else {
                    for (Map.Entry<?, ?> colEntry : subMap.entrySet()) {
                        String colName = Objects.toString(colEntry.getKey(), "");
                        flatRow.put(key + "." + colName, colEntry.getValue());
                        flatRow.put(colName, colEntry.getValue());
                    }
                }
            } else if (value instanceof List<?> listVal) {
                Long subModId = null;
                try {
                    subModId = Long.valueOf(key);
                } catch (Exception ignored) {
                }

                List<DataRecordItem> childItems = new ArrayList<>();
                for (Object item : listVal) {
                    if (item instanceof Map<?, ?> castedMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapItem = (Map<String, Object>) castedMap;
                        childItems.add(buildDataRecordItem(subModId, null, mapItem));
                    }
                }
                ModuleDataNode childNode =
                        ModuleDataNode.builder().moduleId(subModId).record(childItems).build();
                childrenNodes.add(childNode);
            } else {
                flatRow.put(key, value);
            }
        }

        return DataRecordItem.builder().row(flatRow).children(childrenNodes).build();
    }

    private List<ModuleMetaDTO> buildModuleMetaList(Long rootModuleId, Long menuId, Long roleId) {
        List<ModuleMetaDTO> metaList = new ArrayList<>();
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        if (moduleTree == null || moduleTree.getModuleMap() == null) {
            return metaList;
        }

        Map<Long, ModulePermissionDTO> permMap = null;
        if (roleId != null && moduleTree.getAllowedModuleIds() != null) {
            try {
                permMap = configApi.getModulePermissions(roleId, moduleTree.getAllowedModuleIds());
            } catch (Exception ex) {
                log.warn("获取模块字段权限映射失败: roleId={}, err={}", roleId, ex.getMessage());
            }
        }

        for (Map.Entry<Long, com.jdec.platform.config.api.dto.common.ModuleInfo> e :
                moduleTree.getModuleMap().entrySet()) {
            Long modId = e.getKey();
            com.jdec.platform.config.api.dto.common.ModuleInfo modInfo = e.getValue();
            if (modInfo == null) {
                continue;
            }

            Map<String, FieldMetaDTO> fieldMetaMap = new LinkedHashMap<>();
            ModulePermissionDTO modPerm = permMap != null ? permMap.get(modId) : null;

            if (modInfo.getFields() != null) {
                String primaryTable = modInfo.getPrimaryTable();
                for (ModuleFieldDTO f : modInfo.getFields()) {
                    String col = f.getColumnName();
                    String fullKey =
                            (primaryTable != null && !primaryTable.isBlank())
                                    ? primaryTable + "." + col
                                    : col;

                    ModulePermissionDTO.FieldPermissionItem fp =
                            (modPerm != null && modPerm.getFieldPermissions() != null)
                                    ? modPerm.getFieldPermissions().get(f.getId())
                                    : null;

                    FieldMetaDTO fMeta =
                            FieldMetaDTO.builder()
                                    .id(f.getId())
                                    .displayName(f.getDisplayName())
                                    .apply(fp != null ? fp.getApply() : true)
                                    .view(fp != null ? fp.getView() : true)
                                    .edit(fp != null ? fp.getEdit() : true)
                                    .build();

                    fieldMetaMap.put(fullKey, fMeta);
                }
            }

            metaList.add(
                    ModuleMetaDTO.builder()
                            .moduleId(modId)
                            .menuId(Objects.equals(modId, rootModuleId) ? menuId : null)
                            .fields(fieldMetaMap)
                            .build());
        }

        return metaList;
    }

    private MenuDataResp assembleMenuDataResp(
            MenuHeaderColumnResp headerRoot,
            Long menuId,
            Long rootModuleId,
            Long roleId,
            ModuleDataNode dataNode,
            Map<Long, List<Map<String, Object>>> allBtnsMap) {

        // 1. 获取模块权限汇总
        ModuleSimplePermissionDTO permission =
                ModuleSimplePermissionDTO.builder().apply(1).view(1).edit(1).build();
        if (roleId != null && rootModuleId != null) {
            try {
                Map<Long, ModulePermissionDTO> permMap =
                        configApi.getModulePermissions(
                                roleId, Collections.singletonList(rootModuleId));
                if (permMap != null && permMap.get(rootModuleId) != null) {
                    ModulePermissionDTO p = permMap.get(rootModuleId);
                    permission =
                            ModuleSimplePermissionDTO.builder()
                                    .apply(Boolean.TRUE.equals(p.getApply()) ? 1 : 0)
                                    .view(Boolean.TRUE.equals(p.getView()) ? 1 : 0)
                                    .edit(Boolean.TRUE.equals(p.getEdit()) ? 1 : 0)
                                    .build();
                }
            } catch (Exception ex) {
                log.warn("获取模块权限汇总失败: rootModuleId={}, err={}", rootModuleId, ex.getMessage());
            }
        }

        // 2. 组装每条记录的审批链行信息 (ApprovalRowItem)
        List<ApprovalRowItem> approvalRows = new ArrayList<>();
        if (dataNode != null && dataNode.getRecord() != null && allBtnsMap != null) {
            for (DataRecordItem item : dataNode.getRecord()) {
                if (item != null && item.getRow() != null) {
                    Object idObj = item.getRow().get("id");
                    if (idObj instanceof Number num) {
                        Long recordId = num.longValue();
                        List<Map<String, Object>> btns =
                                allBtnsMap.getOrDefault(recordId, Collections.emptyList());
                        Map<String, Object> rowPayload = new LinkedHashMap<>();
                        rowPayload.put("id", recordId);
                        rowPayload.put("btnList", btns);
                        approvalRows.add(ApprovalRowItem.builder().row(rowPayload).build());
                    }
                }
            }
        }

        return MenuDataResp.builder()
                .header(headerRoot)
                .permission(permission)
                .approval(approvalRows)
                .data(dataNode)
                .build();
    }

    private ApprovalButtonsGroup queryApprovalButtons(Long rootModuleId, Integer approvalType) {
        Map<Long, List<Map<String, Object>>> relevantBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> allBtns = new LinkedHashMap<>();

        if (approvalType == null) {
            return new ApprovalButtonsGroup(relevantBtns, allBtns);
        }

        try {
            List<Map<String, Object>> approvalRawList =
                    approvalEngine.getListButton(rootModuleId, approvalType);
            if (approvalRawList != null && !approvalRawList.isEmpty()) {
                for (Map<String, Object> rawBtn : approvalRawList) {
                    if (rawBtn == null) {
                        continue;
                    }
                    Long recordId = parseRecordIdFromRaw(rawBtn.get("recordId"));
                    if (recordId == null) {
                        continue;
                    }

                    Map<String, Object> btnInfo = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : rawBtn.entrySet()) {
                        if (!"recordId".equals(e.getKey())) {
                            btnInfo.put(e.getKey(), e.getValue());
                        }
                    }

                    relevantBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                    allBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                }
            }
        } catch (Exception ex) {
            log.warn(
                    "获取审批链按钮数据失败或当前模块未配置审批流: moduleId={}, approvalType={}, err={}",
                    rootModuleId,
                    approvalType,
                    ex.getMessage());
        }
        return new ApprovalButtonsGroup(relevantBtns, allBtns);
    }

    private ApprovalFilterResult buildApprovalFilters(
            Integer approvalType,
            Long rootModuleId,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {

        List<FilterItem> filters = new ArrayList<>();
        if (approvalType == null) {
            return new ApprovalFilterResult(filters, false);
        }

        Long currentUserId = UserContext.getUserId();

        if (approvalType == 1) {
            // 【待办】: (id IN recordIds) OR (created_by == currentUserId && business_status == 1)
            Set<Long> todoRecordIds = btnGroupMap.keySet();
            boolean hasApprovalIds = !todoRecordIds.isEmpty() && rootFieldIds.idFieldId() != null;
            boolean canQueryDraft =
                    currentUserId != null
                            && rootFieldIds.createdByFieldId() != null
                            && rootFieldIds.businessStatusFieldId() != null;

            if (!hasApprovalIds && !canQueryDraft) {
                return new ApprovalFilterResult(filters, true);
            }

            FilterItem approvalBranch = null;
            if (hasApprovalIds) {
                approvalBranch =
                        FilterItem.builder()
                                .fieldId(rootFieldIds.idFieldId())
                                .operator("IN")
                                .value(new ArrayList<>(todoRecordIds))
                                .build();
            }

            FilterItem draftBranch = null;
            if (canQueryDraft) {
                List<FilterItem> draftItems = new ArrayList<>();
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.createdByFieldId())
                                .operator("EQ")
                                .value(currentUserId)
                                .build());
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.businessStatusFieldId())
                                .operator("EQ")
                                .value(1)
                                .build());
                draftBranch = FilterItem.builder().logic("AND").children(draftItems).build();
            }

            if (approvalBranch != null && draftBranch != null) {
                filters.add(
                        FilterItem.builder()
                                .logic("OR")
                                .children(Arrays.asList(approvalBranch, draftBranch))
                                .build());
            } else if (approvalBranch != null) {
                filters.add(approvalBranch);
            } else {
                filters.add(draftBranch);
            }
        } else if (approvalType == 2) {
            // 【已办】: id IN doneRecordIds
            Set<Long> doneRecordIds = btnGroupMap.keySet();
            if (doneRecordIds.isEmpty() || rootFieldIds.idFieldId() == null) {
                return new ApprovalFilterResult(filters, true);
            }
            filters.add(
                    FilterItem.builder()
                            .fieldId(rootFieldIds.idFieldId())
                            .operator("IN")
                            .value(new ArrayList<>(doneRecordIds))
                            .build());
        } else if (approvalType == 3) {
            // 【全部】: (id IN allApprovalRecordIds) OR (created_by == currentUserId && business_status
            // == 1)
            Set<Long> allApprovalRecordIds = btnGroupMap.keySet();
            boolean hasApprovalIds =
                    !allApprovalRecordIds.isEmpty() && rootFieldIds.idFieldId() != null;
            boolean canQueryDraft =
                    currentUserId != null
                            && rootFieldIds.createdByFieldId() != null
                            && rootFieldIds.businessStatusFieldId() != null;

            if (!hasApprovalIds && !canQueryDraft) {
                return new ApprovalFilterResult(filters, true);
            }

            FilterItem approvalBranch = null;
            if (hasApprovalIds) {
                approvalBranch =
                        FilterItem.builder()
                                .fieldId(rootFieldIds.idFieldId())
                                .operator("IN")
                                .value(new ArrayList<>(allApprovalRecordIds))
                                .build();
            }

            FilterItem draftBranch = null;
            if (canQueryDraft) {
                List<FilterItem> draftItems = new ArrayList<>();
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.createdByFieldId())
                                .operator("EQ")
                                .value(currentUserId)
                                .build());
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.businessStatusFieldId())
                                .operator("EQ")
                                .value(1)
                                .build());
                draftBranch = FilterItem.builder().logic("AND").children(draftItems).build();
            }

            if (approvalBranch != null && draftBranch != null) {
                filters.add(
                        FilterItem.builder()
                                .logic("OR")
                                .children(Arrays.asList(approvalBranch, draftBranch))
                                .build());
            } else if (approvalBranch != null) {
                filters.add(approvalBranch);
            } else {
                filters.add(draftBranch);
            }
        }

        return new ApprovalFilterResult(filters, false);
    }

    private RootModuleFieldIds extractRootModuleFieldIds(Long rootModuleId) {
        if (rootModuleId == null) {
            return new RootModuleFieldIds(null, null, null);
        }

        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        Long idFieldId = null;
        Long createdByFieldId = null;
        Long businessStatusFieldId = null;

        if (moduleTree != null && moduleTree.getRootModule() != null) {
            List<ModuleFieldDTO> fields = moduleTree.getRootModule().getFields();
            if (fields != null) {
                for (ModuleFieldDTO f : fields) {
                    String col = f.getColumnName();
                    if ("id".equalsIgnoreCase(col)) {
                        idFieldId = f.getId();
                    } else if ("created_by".equalsIgnoreCase(col)
                            || "createdBy".equalsIgnoreCase(col)) {
                        createdByFieldId = f.getId();
                    } else if ("business_status".equalsIgnoreCase(col)
                            || "businessStatus".equalsIgnoreCase(col)) {
                        businessStatusFieldId = f.getId();
                    }
                }
            }
        }

        return new RootModuleFieldIds(idFieldId, createdByFieldId, businessStatusFieldId);
    }

    private Long parseRecordIdFromRaw(Object rIdObj) {
        if (rIdObj instanceof Number num) {
            return num.longValue();
        } else if (rIdObj != null) {
            try {
                return Long.valueOf(rIdObj.toString());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private record ApprovalButtonsGroup(
            Map<Long, List<Map<String, Object>>> relevantBtns,
            Map<Long, List<Map<String, Object>>> allBtns) {}

    private record RootModuleFieldIds(
            Long idFieldId, Long createdByFieldId, Long businessStatusFieldId) {}

    private record ApprovalFilterResult(List<FilterItem> filters, boolean shortCircuitEmpty) {}
}
