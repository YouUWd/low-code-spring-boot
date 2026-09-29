package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.response.ApprovalRowItem;
import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.FieldMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleDataNode;
import com.jdec.platform.common.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.common.biz.dto.request.FlatFilterItem;
import com.jdec.platform.common.biz.dto.request.MenuQueryReq;
import com.jdec.platform.common.biz.dto.response.MenuDataResp;
import com.jdec.platform.common.biz.dto.response.MenuDetailResp;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 公共业务聚合服务实现 (基于菜单的数据隔离、动态列表与详情聚合) */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonAggregateServiceImpl implements CommonAggregateService {

    private final UserMenuHeaderApi userMenuHeaderApi;
    private final OpenMenuApi openMenuApi;
    private final ConfigApi configApi;
    private final DataApi dataApi;
    private final IApprovalEngine approvalEngine;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;

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

        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());

        List<Long> checkedFieldIds =
                collectQueryFieldIds(headerRoot, req, rootModuleId, moduleTree, projectRelations);

        // 2. 解析根模块核心字段 ID (id, created_by, business_status)
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId);

        // 3. 构建底层通用 QueryReq 并组装通用过滤/排序/分页
        int pageNo = (req != null && req.getPageNo() != null) ? req.getPageNo() : 1;
        int pageSize = (req != null && req.getPageSize() != null) ? req.getPageSize() : 20;

        PageQueryReq.PageQueryReqBuilder<?, ?> queryReqBuilder =
                PageQueryReq.builder()
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
        boolean hasApprovalChain = false;
        try {
            hasApprovalChain =
                    Boolean.TRUE.equals(
                            sysApprovalChainConfigApi.getApprovalStatus(
                                    Collections.singletonList(rootModuleId)));
        } catch (Exception ex) {
            log.warn("查询模块审批链状态失败: rootModuleId={}, err={}", rootModuleId, ex.getMessage());
        }

        Map<Long, List<Map<String, Object>>> allBtnsMap = null;
        if (hasApprovalChain) {
            ApprovalButtonsGroup approvalGroup = queryApprovalButtons(rootModuleId, approvalType);
            Map<Long, List<Map<String, Object>>> approvalBtnMap = approvalGroup.relevantBtns();
            allBtnsMap = approvalGroup.allBtns();

            ApprovalFilterResult approvalFilterResult =
                    buildApprovalFilters(approvalType, rootFieldIds, approvalBtnMap);
            if (approvalFilterResult.shortCircuitEmpty()) {
                // 短路直接返回空数据
                ModuleDataNode emptyNode =
                        ModuleDataNode.builder()
                                .moduleId(rootModuleId)
                                .menuId(menuId)
                                .total(0L)
                                .record(Collections.emptyList())
                                .build();

                return assembleMenuDataResp(
                        headerRoot, menuId, rootModuleId, roleId, emptyNode, allBtnsMap);
            }

            if (!approvalFilterResult.filters().isEmpty()) {
                finalFilters.addAll(approvalFilterResult.filters());
            }
        }
        queryReqBuilder.filters(finalFilters);

        // 5. 调用底层低代码数据引擎进行分页查询
        DataPage<DataBlock> rawDataPage = dataApi.query(queryReqBuilder.build());

        // 6. 将 DataRow 数据集结构转换为自相似同构树节点 ModuleDataNode
        Long total =
                (rawDataPage != null && rawDataPage.getTotal() != null)
                        ? rawDataPage.getTotal()
                        : 0L;
        ModuleDataNode rootNode =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .total(total)
                        .record(new ArrayList<>())
                        .build();

        if (rawDataPage != null && rawDataPage.getRecords() != null) {
            String rootModKey = String.valueOf(rootModuleId);
            for (DataRow rawRow : rawDataPage.getRecords()) {
                if (rawRow == null) {
                    continue;
                }
                rootNode.getRecord()
                        .add(buildDataRecordItemFromDataRow(rootModuleId, menuId, rawRow));
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
        DataBlock dataBlock = dataApi.getDetail(rootModuleId, id, checkedFieldIds);

        // 3. 将 DataRow 数据转换为 ModuleDataNode 树
        ModuleDataNode dataTree =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .record(new ArrayList<>())
                        .build();

        if (dataBlock != null
                && dataBlock.getRecords() != null
                && !dataBlock.getRecords().isEmpty()) {
            DataRow rawRow = dataBlock.getRecords().get(0);
            dataTree.getRecord().add(buildDataRecordItemFromDataRow(rootModuleId, menuId, rawRow));
        }

        // 4. 构建模块元数据配置列表 (ModuleMetaDTO)
        List<ModuleMetaDTO> metaList = buildModuleMetaList(rootModuleId, menuId, roleId);

        // 5. 组装响应结构
        return MenuDetailResp.builder().meta(metaList).data(dataTree).build();
    }

    @Override
    public List<DataOption> getOptionsByMenu(
            Long menuId, com.jdec.platform.common.biz.dto.request.MenuOptionReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (req == null || req.getFieldId() == null) {
            throw new IllegalArgumentException("目标字段 ID (fieldId) 不能为空");
        }

        // 1. 通过 OpenMenuApi 获取菜单关联的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联有效的根模块 ID");
        }

        // 2. 组装单层 AND 组合过滤条件
        List<FilterItem> finalFilters = new ArrayList<>();
        if (req.getFilters() != null && !req.getFilters().isEmpty()) {
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

        // 3. 审批流过滤注入 (1-待办, 2-已办, 3-全部)
        Integer approvalType = req.getApprovalType();
        boolean hasApprovalChain = false;
        try {
            hasApprovalChain =
                    Boolean.TRUE.equals(
                            sysApprovalChainConfigApi.getApprovalStatus(
                                    Collections.singletonList(rootModuleId)));
        } catch (Exception ex) {
            log.warn("查询模块审批链状态失败: rootModuleId={}, err={}", rootModuleId, ex.getMessage());
        }

        if (hasApprovalChain && approvalType != null) {
            RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId);
            ApprovalButtonsGroup approvalGroup = queryApprovalButtons(rootModuleId, approvalType);
            Map<Long, List<Map<String, Object>>> approvalBtnMap = approvalGroup.relevantBtns();

            ApprovalFilterResult approvalFilterResult =
                    buildApprovalFilters(approvalType, rootFieldIds, approvalBtnMap);
            if (approvalFilterResult.shortCircuitEmpty()) {
                // 审批过滤无候选匹配时短路直接返回空候选项列表
                return Collections.emptyList();
            }

            if (!approvalFilterResult.filters().isEmpty()) {
                finalFilters.addAll(approvalFilterResult.filters());
            }
        }

        // 4. 构建 OptionReq 并调用底层低代码数据引擎
        OptionReq optionReq =
                OptionReq.builder()
                        .moduleId(rootModuleId)
                        .fieldId(req.getFieldId())
                        .filters(finalFilters)
                        .direction(req.getDirection())
                        .build();

        return dataApi.getOptions(optionReq);
    }

    private List<Long> collectQueryFieldIds(
            MenuHeaderColumnResp headerRoot,
            MenuQueryReq req,
            Long rootModuleId,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations) {
        List<Long> result = new ArrayList<>();
        if (headerRoot == null) {
            return result;
        }

        // 1. 解析请求中的过滤条件，提取命中的 1:N 顶层分支
        Set<Long> hitOneToManyBranches = new LinkedHashSet<>();
        if (req != null && !CollectionUtils.isEmpty(req.getFilters())) {
            for (FlatFilterItem flatFilter : req.getFilters()) {
                if (flatFilter != null && flatFilter.getFieldId() != null) {
                    Long fieldId = flatFilter.getFieldId();
                    Long fieldModuleId =
                            moduleTree != null ? moduleTree.getModuleIdByFieldId(fieldId) : null;
                    if (fieldModuleId == null) {
                        fieldModuleId = findModuleIdByFieldIdInHeader(headerRoot, fieldId);
                    }
                    Long top1NBranch =
                            resolveTopOneToManyBranch(
                                    fieldModuleId, rootModuleId, moduleTree, projectRelations);
                    if (top1NBranch != null) {
                        hitOneToManyBranches.add(top1NBranch);
                    }
                }
            }
        }

        // 2. 1:N 模块校验：最多只允许 1 个 1:N 模块或菜单参与筛选
        if (hitOneToManyBranches.size() > 1) {
            throw new BusinessException(
                    400,
                    "带搜索条件查询时，最多只允许 1 个 1:N 模块参与筛选，当前搜索条件涉及 "
                            + hitOneToManyBranches.size()
                            + " 个 1:N 模块分支");
        }

        Long active1NBranch =
                hitOneToManyBranches.isEmpty() ? null : hitOneToManyBranches.iterator().next();

        // 3. 递归收集字段 (1:1 始终保留；1:N 仅保留命中分支或全量保留)
        collectEligibleFieldIds(
                headerRoot, result, rootModuleId, active1NBranch, moduleTree, projectRelations);

        return result;
    }

    private void collectEligibleFieldIds(
            MenuHeaderColumnResp node,
            List<Long> result,
            Long rootModuleId,
            Long active1NBranch,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations) {
        if (node == null) {
            return;
        }

        Long nodeModuleId = node.getModuleId();
        Long top1NBranch =
                resolveTopOneToManyBranch(nodeModuleId, rootModuleId, moduleTree, projectRelations);

        // 如果该节点/字段属于 1:N 模块，且当前有激活的 1:N 筛选分支，但不是当前激活分支，则跳过
        boolean isExcluded =
                top1NBranch != null
                        && active1NBranch != null
                        && !top1NBranch.equals(active1NBranch);

        if (!isExcluded) {
            if (node.getFieldId() != null) {
                result.add(node.getFieldId());
            }
            if (node.getChildren() != null) {
                for (MenuHeaderColumnResp child : node.getChildren()) {
                    collectEligibleFieldIds(
                            child,
                            result,
                            rootModuleId,
                            active1NBranch,
                            moduleTree,
                            projectRelations);
                }
            }
        }
    }

    private Long findModuleIdByFieldIdInHeader(MenuHeaderColumnResp node, Long fieldId) {
        if (node == null || fieldId == null) {
            return null;
        }
        if (fieldId.equals(node.getFieldId())) {
            return node.getModuleId();
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                Long found = findModuleIdByFieldIdInHeader(child, fieldId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Long resolveTopOneToManyBranch(
            Long moduleId,
            Long rootModuleId,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations) {
        if (moduleId == null || rootModuleId == null || moduleId.equals(rootModuleId)) {
            return null;
        }
        if (moduleTree == null) {
            return null;
        }

        // 1. 沿父节点链向上查找直到根模块
        List<Long> pathFromChildToRoot = new ArrayList<>();
        Long curr = moduleId;
        Set<Long> visited = new HashSet<>();
        while (curr != null && !curr.equals(rootModuleId) && visited.add(curr)) {
            pathFromChildToRoot.add(curr);
            ModuleInfo m = moduleTree.getModule(curr);
            if (m == null || m.getParentId() == null || m.getParentId() == 0) {
                break;
            }
            curr = m.getParentId();
        }

        if (pathFromChildToRoot.isEmpty()) {
            return null;
        }

        // 2. 逆转为从根向下的路径: [直接子模块, 孙模块, ..., 目标模块]
        Collections.reverse(pathFromChildToRoot);

        Long prevModuleId = rootModuleId;
        for (Long stepModuleId : pathFromChildToRoot) {
            ModuleInfo parentMeta = moduleTree.getModule(prevModuleId);
            ModuleInfo childMeta = moduleTree.getModule(stepModuleId);
            String relType =
                    getModuleRelationType(parentMeta, childMeta, projectRelations, moduleTree);
            if ("1:N".equalsIgnoreCase(relType)) {
                // 命中首个 1:N 关联，返回该顶层 1:N 分支的 moduleId
                return stepModuleId;
            }
            prevModuleId = stepModuleId;
        }

        // 全链路为 1:1
        return null;
    }

    private String getModuleRelationType(
            ModuleInfo parentMeta,
            ModuleInfo childMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        if (parentMeta == null || childMeta == null) {
            return "1:1";
        }
        String childPrimary = childMeta.getPrimaryTable();
        if (childPrimary == null || childPrimary.isBlank()) {
            return "1:1";
        }

        ModuleInfo physicalParent = parentMeta;
        String parentPrimary = physicalParent.getPrimaryTable();
        Long curP = parentMeta.getParentId();
        Set<Long> visited = new HashSet<>();
        while ((parentPrimary == null || parentPrimary.isBlank())
                && curP != null
                && curP > 0
                && visited.add(curP)) {
            physicalParent = tree != null ? tree.getModule(curP) : null;
            if (physicalParent != null) {
                parentPrimary = physicalParent.getPrimaryTable();
                curP = physicalParent.getParentId();
            } else {
                break;
            }
        }

        if (parentPrimary == null || parentPrimary.isBlank()) {
            return "1:N";
        }

        if (parentPrimary.equalsIgnoreCase(childPrimary)) {
            return "1:1";
        }

        if (projectRelations != null) {
            TableRelationDTO matchedRel =
                    projectRelations.findDirectRelation(parentPrimary, childPrimary);
            if (matchedRel != null
                    && matchedRel.getRelationType() != null
                    && !matchedRel.getRelationType().isBlank()) {
                return matchedRel.getRelationType().trim().toUpperCase();
            }
        }

        return "1:N";
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

    private DataRecordItem buildDataRecordItemFromDataRow(
            Long moduleId, Long menuId, DataRow dataRow) {
        if (dataRow == null) {
            return DataRecordItem.builder().row(Collections.emptyMap()).build();
        }

        Map<String, Object> flatRow = new LinkedHashMap<>();
        List<ModuleDataNode> childrenNodes = new ArrayList<>();

        // 1. 展平当前数据行的物理表字段 (e.g. info.company_name)
        if (dataRow.getTables() != null) {
            for (Map.Entry<String, Object> entry : dataRow.getTables().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> subMap) {
                    for (Map.Entry<?, ?> colEntry : subMap.entrySet()) {
                        String colName = Objects.toString(colEntry.getKey(), "");
                        if (!colName.isBlank()) {
                            flatRow.put(key + "." + colName, colEntry.getValue());
                        }
                    }
                } else {
                    flatRow.put(key, value);
                }
            }
        }

        // 2. 递归处理从属于当前行的子模块数据节点 DataBlock (自相似树)
        if (dataRow.getChildren() != null) {
            for (com.jdec.platform.data.api.dto.common.DataBlock childNode :
                    dataRow.getChildren()) {
                if (childNode == null) {
                    continue;
                }
                Long subModId = childNode.getModuleId();
                List<DataRecordItem> childItems = new ArrayList<>();
                if (childNode.getRecords() != null) {
                    for (DataRow subRow : childNode.getRecords()) {
                        if (subRow != null) {
                            childItems.add(buildDataRecordItemFromDataRow(subModId, null, subRow));
                        }
                    }
                }
                ModuleDataNode moduleDataNode =
                        ModuleDataNode.builder().moduleId(subModId).record(childItems).build();
                childrenNodes.add(moduleDataNode);
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
        List<ApprovalRowItem> approvalRows = null;
        if (allBtnsMap != null && dataNode != null && dataNode.getRecord() != null) {
            approvalRows = new ArrayList<>();
            for (DataRecordItem item : dataNode.getRecord()) {
                if (item != null && item.getRow() != null) {
                    Object idObj = item.getRow().get("id");
                    if (idObj == null) {
                        for (Map.Entry<String, Object> entry : item.getRow().entrySet()) {
                            if ("id".equalsIgnoreCase(entry.getKey())
                                    || entry.getKey().endsWith(".id")
                                    || entry.getKey().endsWith(".ID")) {
                                idObj = entry.getValue();
                                break;
                            }
                        }
                    }
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
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {

        List<FilterItem> filters = new ArrayList<>();
        if (approvalType == null) {
            return new ApprovalFilterResult(filters, false);
        }

        Long currentUserId = UserContext.getUserId();

        if (approvalType == 1) {
            // 【待办】: (id IN recordIds) OR (created_by == currentUserId && business_status ==
            // 1)
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
            // 【全部】: (id IN allApprovalRecordIds) OR (created_by == currentUserId &&
            // business_status
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
