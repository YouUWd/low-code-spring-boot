package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.FieldSortItem;
import com.jdec.platform.common.api.dto.request.MenuFlatFieldFilterItem;
import com.jdec.platform.common.api.dto.request.MenuOptionReq;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.response.*;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
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
    private final CommonFilterResolver approvalFilterResolver;
    private final SysUserApi sysUserApi;
    private final SysStatusApi sysStatusApi;

    @Override
    public MenuDataResp queryByMenu(Long menuId, MenuQueryReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        Long userId = getEffectiveUserId();
        Long roleId = UserContext.getRoleId();

        // 1. 获取用户在该菜单下的个性化表头展示配置 (用于提取已勾选字段与多级表头)
        MenuHeaderColumnResp headerRoot = getHeaderDisplayOrThrow(userId, menuId, roleId);
        Long rootModuleId = headerRoot.getModuleId();

        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());

        // 2. 识别并校验请求中的 1:N 模块分支，单次递归遍历表头同时完成表头修饰与字段收集
        Map<Long, Long> branchCache = new HashMap<>();
        Long active1NBranch =
                resolveActiveOneToManyBranch(
                        headerRoot, req, rootModuleId, moduleTree, projectRelations, branchCache);

        List<Long> checkedFieldIds = new ArrayList<>();
        processHeaderAndCollectFieldIds(
                headerRoot,
                checkedFieldIds,
                rootModuleId,
                active1NBranch,
                moduleTree,
                projectRelations,
                branchCache);

        // 2. 将菜单元数据 fieldId 转换为 data-api 的物理表/列请求
        int pageNo = (req != null && req.getPageNo() != null) ? req.getPageNo() : 1;
        int pageSize = (req != null && req.getPageSize() != null) ? req.getPageSize() : 20;
        List<TableSpec> tableSpecs = PhysicalQueryMapper.toTableSpecs(moduleTree, checkedFieldIds);
        List<com.jdec.platform.data.api.dto.request.FilterItem> finalFilters =
                req != null
                        ? PhysicalQueryMapper.toTableFilters(moduleTree, req.getFilters())
                        : new ArrayList<>();
        List<com.jdec.platform.data.api.dto.request.SortItem> finalSorts =
                req != null
                        ? PhysicalQueryMapper.toTableSorts(moduleTree, req.getSorts())
                        : new ArrayList<>();

        String primaryTable =
                (moduleTree != null && moduleTree.getRootModule() != null)
                        ? moduleTree.getRootModule().getPrimaryTable()
                        : null;

        // 3. 审批流过滤注入 (1-待办, 2-已办, 3-全部)
        Integer approvalType = req != null ? req.getApprovalType() : null;
        CommonFilterResolver.ApprovalFilterItem approvalFilter =
                approvalFilterResolver.resolveApprovalFilters(
                        rootModuleId, approvalType, moduleTree);

        if (approvalFilter.shortCircuitEmpty()) {
            ModuleDataNode emptyNode =
                    ModuleDataNode.builder()
                            .moduleId(rootModuleId)
                            .menuId(menuId)
                            .total(0L)
                            .record(Collections.emptyList())
                            .build();

            List<ApprovalRowItem> emptyApprovalRows =
                    approvalFilter.btnMap() != null ? Collections.emptyList() : null;

            return assembleMenuDataResp(
                    headerRoot, menuId, rootModuleId, roleId, emptyNode, emptyApprovalRows);
        }

        if (!approvalFilter.filters().isEmpty()) {
            finalFilters.addAll(approvalFilter.filters());
        }

        // 4. 调用底层低代码数据引擎进行分页查询
        DataPage<DataBlock> rawDataPage =
                dataApi.queryPage(
                        PageQueryReq.builder()
                                .moduleId(rootModuleId)
                                .tables(tableSpecs)
                                .filters(finalFilters)
                                .sorts(finalSorts)
                                .pageNo(pageNo)
                                .pageSize(pageSize)
                                .build());

        // 5. 将 DataRow 数据集结构转换为自相似同构树节点 ModuleDataNode 与 ApprovalRowItem 列表
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

        List<ApprovalRowItem> approvalRows =
                approvalFilter.btnMap() != null ? new ArrayList<>() : null;

        if (rawDataPage != null && rawDataPage.getRecords() != null) {
            enhanceDataRows(rawDataPage.getRecords(), rootModuleId, primaryTable);
            for (DataRow rawRow : rawDataPage.getRecords()) {
                if (rawRow == null) {
                    continue;
                }
                rootNode.getRecord()
                        .add(buildDataRecordItemFromDataRow(rootModuleId, menuId, rawRow));

                if (approvalRows != null) {
                    Long recordId = rawRow.getLong(primaryTable, "id");
                    if (recordId != null) {
                        List<Map<String, Object>> btns = approvalFilter.btnMap().get(recordId);
                        if (CollectionUtils.isEmpty(btns)) {
                            boolean isHandle =
                                    approvalType != null
                                            && (approvalType == 1 || approvalType == 3);
                            String buttonKey = isHandle ? "handle" : "view";
                            String buttonName = isHandle ? "办理" : "查阅";
                            Map<String, Object> defaultBtn = new LinkedHashMap<>();
                            defaultBtn.put("buttonKey", buttonKey);
                            defaultBtn.put("buttonName", buttonName);
                            btns = Collections.singletonList(defaultBtn);
                        }
                        Map<String, Object> rowPayload = new LinkedHashMap<>();
                        rowPayload.put("id", recordId);
                        rowPayload.put("btnList", btns);
                        approvalRows.add(ApprovalRowItem.builder().row(rowPayload).build());
                    }
                }
            }
        }

        // 6. 组装并返回复合响应
        return assembleMenuDataResp(
                headerRoot, menuId, rootModuleId, roleId, rootNode, approvalRows);
    }

    @Override
    public MenuDetailResp getDetailByMenu(Long menuId, Long id) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (id == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }

        Long userId = getEffectiveUserId();
        Long roleId = UserContext.getRoleId();

        // 1. 获取表头展示配置提取 rootModuleId
        MenuHeaderColumnResp headerRoot = getHeaderDisplayOrThrow(userId, menuId, roleId);
        Long rootModuleId = headerRoot.getModuleId();

        List<Long> checkedFieldIds = new ArrayList<>();
        collectCheckedFieldIds(headerRoot, checkedFieldIds);
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        List<TableSpec> tableSpecs = PhysicalQueryMapper.toTableSpecs(moduleTree, checkedFieldIds);

        // 2. 调用底层低代码引擎获取指定物理字段投影的单据详情
        DataBlock dataBlock =
                dataApi.getDetail(
                        DetailQueryReq.builder()
                                .moduleId(rootModuleId)
                                .id(id)
                                .tables(tableSpecs)
                                .build());

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
            String primaryTable = extractPrimaryTable(rootModuleId);
            enhanceDataRows(dataBlock.getRecords(), rootModuleId, primaryTable);
            DataRow rawRow = dataBlock.getRecords().get(0);
            dataTree.getRecord().add(buildDataRecordItemFromDataRow(rootModuleId, menuId, rawRow));
        }

        // 4. 构建模块元数据配置列表 (ModuleMetaDTO)
        List<ModuleMetaDTO> metaList = buildModuleMetaList(rootModuleId, menuId, roleId);

        // 5. 组装响应结构
        return MenuDetailResp.builder().meta(metaList).data(dataTree).build();
    }

    @Override
    public List<DataOptionResp> getOptionsByMenu(Long menuId, MenuOptionReq req) {
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

        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        PhysicalQueryMapper.PhysicalFieldRef targetField =
                PhysicalQueryMapper.resolve(moduleTree, req.getFieldId());

        // 2. 组装单层 AND 组合过滤条件
        List<com.jdec.platform.data.api.dto.request.FilterItem> finalFilters =
                PhysicalQueryMapper.toTableFilters(moduleTree, req.getFilters());

        // 3. 审批流过滤注入 (1-待办, 2-已办, 3-全部)
        Integer approvalType = req.getApprovalType();
        CommonFilterResolver.ApprovalFilterItem approvalFilter =
                approvalFilterResolver.resolveApprovalFilters(
                        rootModuleId, approvalType, moduleTree);

        if (approvalFilter.shortCircuitEmpty()) {
            // 审批过滤无候选匹配时短路直接返回空候选项列表
            return Collections.emptyList();
        }

        if (!approvalFilter.filters().isEmpty()) {
            finalFilters.addAll(approvalFilter.filters());
        }

        // 4. 构建 OptionReq 并调用底层低代码数据引擎
        OptionReq optionReq =
                OptionReq.builder()
                        .moduleId(rootModuleId)
                        .tableName(targetField.tableName())
                        .columnName(targetField.columnName())
                        .filters(finalFilters)
                        .direction(req.getDirection())
                        .build();

        List<DataOptionResp> rawOptions = dataApi.getOptions(optionReq);
        return enhanceOptions(rawOptions, targetField.columnName(), rootModuleId);
    }

    private Long resolveActiveOneToManyBranch(
            MenuHeaderColumnResp headerRoot,
            MenuQueryReq req,
            Long rootModuleId,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (headerRoot == null) {
            return null;
        }

        // 1. 解析请求中的过滤条件与排序规则，提取命中的 1:N 顶层分支
        Set<Long> hitOneToManyBranches = new LinkedHashSet<>();
        if (req != null) {
            if (!CollectionUtils.isEmpty(req.getFilters())) {
                for (MenuFlatFieldFilterItem flatFilter : req.getFilters()) {
                    if (flatFilter != null && flatFilter.getFieldId() != null) {
                        Long branch =
                                resolveBranchByFieldId(
                                        flatFilter.getFieldId(),
                                        headerRoot,
                                        rootModuleId,
                                        moduleTree,
                                        projectRelations,
                                        branchCache);
                        if (branch != null) {
                            hitOneToManyBranches.add(branch);
                        }
                    }
                }
            }
            if (!CollectionUtils.isEmpty(req.getSorts())) {
                for (FieldSortItem sortItem : req.getSorts()) {
                    if (sortItem != null && sortItem.getFieldId() != null) {
                        Long branch =
                                resolveBranchByFieldId(
                                        sortItem.getFieldId(),
                                        headerRoot,
                                        rootModuleId,
                                        moduleTree,
                                        projectRelations,
                                        branchCache);
                        if (branch != null) {
                            hitOneToManyBranches.add(branch);
                        }
                    }
                }
            }
        }

        // 2. 1:N 模块校验：最多只允许 1 个 1:N 模块或菜单参与筛选与排序
        if (hitOneToManyBranches.size() > 1) {
            throw new BusinessException(
                    400,
                    "带搜索或排序条件查询时，最多只允许 1 个 1:N 模块参与筛选，当前搜索条件涉及 "
                            + hitOneToManyBranches.size()
                            + " 个 1:N 模块分支");
        }

        return hitOneToManyBranches.isEmpty() ? null : hitOneToManyBranches.iterator().next();
    }

    private Long resolveBranchByFieldId(
            Long fieldId,
            MenuHeaderColumnResp headerRoot,
            Long rootModuleId,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (fieldId == null) {
            return null;
        }
        Long fieldModuleId = moduleTree != null ? moduleTree.getModuleIdByFieldId(fieldId) : null;
        if (fieldModuleId == null) {
            fieldModuleId = findModuleIdByFieldIdInHeader(headerRoot, fieldId);
        }
        return resolveTopOneToManyBranchWithCache(
                fieldModuleId, rootModuleId, moduleTree, projectRelations, branchCache);
    }

    private void processHeaderAndCollectFieldIds(
            MenuHeaderColumnResp node,
            List<Long> checkedFieldIds,
            Long rootModuleId,
            Long active1NBranch,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (node == null) {
            return;
        }

        Long nodeModuleId = node.getModuleId();
        if (nodeModuleId == null && node.getFieldId() != null && moduleTree != null) {
            nodeModuleId = moduleTree.getModuleIdByFieldId(node.getFieldId());
        }
        Long top1NBranch =
                resolveTopOneToManyBranchWithCache(
                        nodeModuleId, rootModuleId, moduleTree, projectRelations, branchCache);

        // 如果该节点/字段属于 1:N 模块，且当前有激活的 1:N 筛选分支，但不是当前激活分支
        boolean isOther1NBranch =
                active1NBranch != null
                        && top1NBranch != null
                        && !top1NBranch.equals(active1NBranch);

        if (isOther1NBranch) {
            // 对其他未命中的 1:N 分支：置为不可搜索、不可排序，且不收集字段
            node.setSearchType(null);
            node.setSorted(0);
        } else {
            // 对根模块、1:1 模块以及当前激活的 1:N 分支：保留原表头配置，并收集字段
            if (node.getFieldId() != null) {
                checkedFieldIds.add(node.getFieldId());
            }
        }

        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                processHeaderAndCollectFieldIds(
                        child,
                        checkedFieldIds,
                        rootModuleId,
                        active1NBranch,
                        moduleTree,
                        projectRelations,
                        branchCache);
            }
        }
    }

    private Long resolveTopOneToManyBranchWithCache(
            Long moduleId,
            Long rootModuleId,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (moduleId == null || rootModuleId == null || moduleId.equals(rootModuleId)) {
            return null;
        }
        if (branchCache != null && branchCache.containsKey(moduleId)) {
            return branchCache.get(moduleId);
        }
        Long branch =
                resolveTopOneToManyBranch(moduleId, rootModuleId, moduleTree, projectRelations);
        if (branchCache != null) {
            branchCache.put(moduleId, branch);
        }
        return branch;
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
        while (!curr.equals(rootModuleId) && visited.add(curr)) {
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

        // 1. 展平当前数据行的物理表字段 (通过 DataRow.getTable 获取表结构化映射)
        if (dataRow.getTables() != null) {
            for (String tableName : dataRow.getTables().keySet()) {
                Map<String, Object> tableData = dataRow.getTable(tableName);
                if (tableData != null) {
                    for (Map.Entry<String, Object> colEntry : tableData.entrySet()) {
                        if (colEntry.getKey() != null && !colEntry.getKey().isBlank()) {
                            flatRow.put(tableName + "." + colEntry.getKey(), colEntry.getValue());
                        }
                    }
                }
            }
        }

        // 2. 递归处理从属于当前行的子模块数据节点 DataBlock (自相似树)
        if (dataRow.getChildren() != null) {
            for (DataBlock childNode : dataRow.getChildren()) {
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

        for (Map.Entry<Long, ModuleInfo> e : moduleTree.getModuleMap().entrySet()) {
            Long modId = e.getKey();
            ModuleInfo modInfo = e.getValue();
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
            List<ApprovalRowItem> approvalRows) {

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

        return MenuDataResp.builder()
                .header(headerRoot)
                .permission(permission)
                .approval(approvalRows)
                .data(dataNode)
                .build();
    }

    /**
     * 在 DataRow 阶段对主表数据进行元数据增强（created_by 转换为 UserSimpleResp，business_status 转换为 StatusSimpleResp）
     */
    private void enhanceDataRows(List<DataRow> records, Long rootModuleId, String primaryTable) {
        if (CollectionUtils.isEmpty(records) || primaryTable == null || primaryTable.isBlank()) {
            return;
        }

        // 1. 批量收集主表记录中的 created_by 与 business_status
        Set<Long> userIds = new HashSet<>();
        Set<Integer> statusValues = new HashSet<>();
        for (DataRow row : records) {
            if (row == null) {
                continue;
            }
            Long uid = row.getLong(primaryTable, "created_by");
            if (uid != null) {
                userIds.add(uid);
            }
            Integer sv = row.getInteger(primaryTable, "business_status");
            if (sv != null) {
                statusValues.add(sv);
            }
        }

        // 2. 批量查询用户信息
        Map<Long, UserSimpleResp> userMap = queryUserMap(userIds);

        // 3. 批量查询状态信息
        Map<Integer, StatusSimpleResp> statusMap = queryStatusMap(statusValues, rootModuleId);

        // 4. 将转换后的对象写回 DataRow 主表
        for (DataRow row : records) {
            if (row == null) {
                continue;
            }
            Long uid = row.getLong(primaryTable, "created_by");
            if (uid != null) {
                UserSimpleResp userResp = userMap.get(uid);
                row.putValue(
                        primaryTable,
                        "created_by",
                        userResp != null ? userResp : UserSimpleResp.builder().userId(uid).build());
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

    /**
     * 对下拉候选值进行特殊处理： 1. created_by：转换为与列表一致的 UserSimpleResp 对象 JSON 2. business_status：转换为与列表一致的
     * StatusSimpleResp 对象 JSON 3. reg_status 等字典字段：底层引擎已解析为结构化 JSON/Map 对象，直接返回即可
     */
    private List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions, String columnName, Long rootModuleId) {
        if (CollectionUtils.isEmpty(rawOptions) || columnName == null || columnName.isBlank()) {
            return rawOptions != null ? rawOptions : Collections.emptyList();
        }

        String col = columnName.trim();

        // 1. created_by 转换为 UserSimpleResp 对象
        if ("created_by".equalsIgnoreCase(col)) {
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
                    UserSimpleResp userResp = userMap.get(uid);
                    UserSimpleResp finalUser =
                            userResp != null
                                    ? userResp
                                    : UserSimpleResp.builder().userId(uid).build();
                    opt.setValue(finalUser);
                    opt.setLabel(finalUser);
                }
            }
            return rawOptions;
        }

        // 2. business_status 转换为 StatusSimpleResp 对象
        if ("business_status".equalsIgnoreCase(col)) {
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
                    StatusSimpleResp statusResp = statusMap.get(sv);
                    StatusSimpleResp finalStatus =
                            statusResp != null
                                    ? statusResp
                                    : StatusSimpleResp.builder().statusValue(sv).build();
                    opt.setValue(finalStatus);
                    opt.setLabel(finalStatus);
                }
            }
            return rawOptions;
        }

        return rawOptions;
    }

    private String extractPrimaryTable(Long rootModuleId) {
        if (rootModuleId == null) {
            return null;
        }
        try {
            ModuleTreeDTO moduleTree =
                    configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
            if (moduleTree != null && moduleTree.getRootModule() != null) {
                return moduleTree.getRootModule().getPrimaryTable();
            }
        } catch (Exception ex) {
            log.warn("获取主模块主表名失败: rootModuleId={}, err={}", rootModuleId, ex.getMessage());
        }
        return null;
    }

    private MenuHeaderColumnResp getHeaderDisplayOrThrow(Long userId, Long menuId, Long roleId) {
        MenuHeaderDisplayResp headerDisplay =
                userMenuHeaderApi.getUserMenuHeaderDisplay(
                        userId, menuId, roleId, null, null, null, null);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }
        MenuHeaderColumnResp headerRoot = headerDisplay.getHeader();
        if (headerRoot.getModuleId() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 对应的表头未关联有效的根模块 ID");
        }
        return headerRoot;
    }

    /** 获取当前生效的用户 ID： 若处于模拟登录模式（mockUserId 与 userId 不一致），取 mockUserId；否则取当前登录用户的 userId。 */
    private Long getEffectiveUserId() {
        return UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
    }
}
