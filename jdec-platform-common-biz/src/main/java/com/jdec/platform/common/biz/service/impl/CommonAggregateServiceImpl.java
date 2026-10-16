package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.FieldSortItem;
import com.jdec.platform.common.api.dto.request.MenuFlatFieldFilterItem;
import com.jdec.platform.common.api.dto.request.MenuListReq;
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
import com.jdec.platform.data.api.dto.request.QueryReq;
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
                    String businessNo = rawRow.getString(primaryTable, "business_no");
                    if (businessNo == null || businessNo.isBlank()) {
                        businessNo = rawRow.getString(primaryTable, "businessNo");
                    }
                    if (recordId != null || (businessNo != null && !businessNo.isBlank())) {
                        List<Map<String, Object>> btns = null;
                        if (businessNo != null && !businessNo.isBlank()) {
                            btns = approvalFilter.btnMap().get(businessNo);
                        }
                        if (CollectionUtils.isEmpty(btns) && recordId != null) {
                            btns = approvalFilter.btnMap().get(String.valueOf(recordId));
                        }
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
                        if (recordId != null) {
                            rowPayload.put("id", recordId);
                        }
                        if (businessNo != null && !businessNo.isBlank()) {
                            rowPayload.put("businessNo", businessNo);
                        }
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
    public List<DataRecordItem> queryList(MenuListReq req) {
        // 1. 根据 tables 中携带的 menuId 解析对应的 moduleId，在项目模块树中定位目标根模块树
        ModuleTreeDTO moduleTree = resolveRootModuleTree(req);
        Long rootModuleId = moduleTree.getRootModuleId();
        String primaryTable =
                (moduleTree.getRootModule() != null)
                        ? moduleTree.getRootModule().getPrimaryTable()
                        : null;

        // 2. 将 MenuTableSpec 转换为 data-api 物理投影表规格 TableSpec
        List<TableSpec> tableSpecs =
                resolveTableSpecs(
                        req != null ? req.getTables() : null,
                        rootModuleId,
                        primaryTable,
                        moduleTree);

        // 3. 将 FlatFilterItem 与 FlatSortItem 转换为 data-api 物理过滤与排序项 (自动解析消歧所属 moduleId)
        List<com.jdec.platform.data.api.dto.request.FilterItem> finalFilters =
                resolveFlatFilters(
                        req != null ? req.getFilters() : null,
                        rootModuleId,
                        primaryTable,
                        moduleTree);
        List<com.jdec.platform.data.api.dto.request.SortItem> finalSorts =
                resolveFlatSorts(
                        req != null ? req.getSorts() : null,
                        rootModuleId,
                        primaryTable,
                        moduleTree);

        // 4. 审批流与数据权限过滤注入 (默认查询全部集合: 3)
        CommonFilterResolver.ApprovalFilterItem approvalFilter =
                approvalFilterResolver.resolveApprovalFilters(rootModuleId, 3, moduleTree);
        if (approvalFilter.shortCircuitEmpty()) {
            return Collections.emptyList();
        }
        if (!approvalFilter.filters().isEmpty()) {
            finalFilters.addAll(approvalFilter.filters());
        }

        // 5. 阶段 1：轻量级调用底层数据引擎查询主表数据，收集符合条件的 business_no 集合
        TableSpec lightSpec =
                TableSpec.builder()
                        .moduleId(rootModuleId)
                        .tableName(primaryTable)
                        .columns(List.of("id", "business_no"))
                        .build();

        DataBlock lightBlock =
                dataApi.queryList(
                        QueryReq.builder()
                                .moduleId(rootModuleId)
                                .tables(List.of(lightSpec))
                                .filters(finalFilters)
                                .sorts(finalSorts)
                                .build());

        List<DataRow> lightRows =
                (lightBlock != null && lightBlock.getRecords() != null)
                        ? lightBlock.getRecords()
                        : Collections.emptyList();

        Set<String> businessNoSet = new LinkedHashSet<>();
        for (DataRow r : lightRows) {
            if (r != null) {
                String bNo = r.getString(primaryTable, "business_no");
                if (bNo != null && !bNo.isBlank()) {
                    businessNoSet.add(bNo.trim());
                }
            }
        }

        if (businessNoSet.isEmpty()) {
            return Collections.emptyList();
        }

        // 5. 阶段 2：通过数据引擎单表 API 在主表上精准查找每个 business_no 最新启用 (business_status = 99) 的最大版本主键 id 列表
        List<Long> latestEnabledIds = dataApi.getLatestVersionIds(primaryTable, businessNoSet, 99);

        if (CollectionUtils.isEmpty(latestEnabledIds)) {
            return Collections.emptyList();
        }

        // 6. 阶段 3：直接基于这批唯一的主键 id 列表，调用底层数据引擎进行全量关联投影查询
        List<com.jdec.platform.data.api.dto.request.FilterItem> targetFilters = new ArrayList<>();
        targetFilters.add(
                com.jdec.platform.data.api.dto.request.FilterItem.builder()
                        .moduleId(rootModuleId)
                        .tableName(primaryTable)
                        .columnName("id")
                        .operator("IN")
                        .value(latestEnabledIds)
                        .build());

        DataBlock enabledBlock =
                dataApi.queryList(
                        QueryReq.builder()
                                .moduleId(rootModuleId)
                                .tables(tableSpecs)
                                .filters(targetFilters)
                                .sorts(finalSorts)
                                .build());

        List<DataRow> finalRows =
                (enabledBlock != null && enabledBlock.getRecords() != null)
                        ? enabledBlock.getRecords()
                        : Collections.emptyList();

        // 6. 收集请求中各模块 ID 对应的菜单 ID 映射关系，供装配子节点 ModuleDataNode 时精准带出 menuId
        Map<Long, Long> moduleToMenuMap = new HashMap<>();
        if (req != null && req.getTables() != null) {
            for (com.jdec.platform.common.api.dto.request.MenuTableSpec mSpec : req.getTables()) {
                if (mSpec != null && mSpec.getMenuId() != null) {
                    Long modId = openMenuApi.getModuleIdByMenuId(mSpec.getMenuId());
                    if (modId != null) {
                        moduleToMenuMap.put(modId, mSpec.getMenuId());
                    }
                }
            }
        }

        // 7. 将筛选后的 DataRow 数据集结构转换为自相似同构树记录列表 List<DataRecordItem> (不返回包含 total 的外层节点)
        List<DataRecordItem> recordList = new ArrayList<>();
        if (!finalRows.isEmpty()) {
            enhanceDataRows(finalRows, rootModuleId, primaryTable);
            Long rootMenuId = moduleToMenuMap.get(rootModuleId);
            for (DataRow rawRow : finalRows) {
                if (rawRow != null) {
                    recordList.add(
                            buildDataRecordItemFromDataRow(
                                    rootModuleId, rootMenuId, rawRow, moduleToMenuMap));
                }
            }
        }

        return recordList;
    }

    /** 根据请求中 tables 规格携带的 menuId 解析对应的 moduleId，在项目的全量模块树列表中定位公共根模块树 */
    private ModuleTreeDTO resolveRootModuleTree(MenuListReq req) {
        if (req == null || CollectionUtils.isEmpty(req.getTables())) {
            throw new IllegalArgumentException("查询请求中的 tables 物理表规格不能为空");
        }

        // 1. 收集所有 MenuTableSpec 中有效的 menuId 并转换为 moduleId
        Set<Long> moduleIds = new LinkedHashSet<>();
        for (com.jdec.platform.common.api.dto.request.MenuTableSpec table : req.getTables()) {
            if (table != null && table.getMenuId() != null) {
                Long mid = openMenuApi.getModuleIdByMenuId(table.getMenuId());
                if (mid != null) {
                    moduleIds.add(mid);
                }
            }
        }

        if (moduleIds.isEmpty()) {
            throw new IllegalArgumentException("tables 规格中必须至少指定一个能关联到有效模块的 menuId");
        }

        // 2. 获取项目及主体下所有根模块的模块树列表
        List<ModuleTreeDTO> projectTrees =
                configApi.getProjectModuleTree(
                        AppContext.getProjectNo(), AppContext.getSubjectId());
        if (CollectionUtils.isEmpty(projectTrees)) {
            throw new BusinessException("当前项目 [" + AppContext.getProjectNo() + "] 下未配置任何模块树");
        }

        // 3. 在所有模块树中找到涵盖所有 moduleIds 的目标树
        ModuleTreeDTO matchedTree = null;
        for (ModuleTreeDTO tree : projectTrees) {
            if (tree != null
                    && tree.getAllowedModuleIds() != null
                    && tree.getAllowedModuleIds().containsAll(moduleIds)) {
                matchedTree = tree;
                break;
            }
        }

        if (matchedTree == null) {
            throw new BusinessException(
                    "所选菜单对应的模块 " + moduleIds + " 跨越了多个不同的业务根模块树或未在当前项目树中找到匹配的公共根模块");
        }

        return matchedTree;
    }

    private List<TableSpec> resolveTableSpecs(
            List<com.jdec.platform.common.api.dto.request.MenuTableSpec> menuTableSpecs,
            Long rootModuleId,
            String primaryTable,
            ModuleTreeDTO moduleTree) {
        if (CollectionUtils.isEmpty(menuTableSpecs)) {
            return null; // 为 null 时底层默认投影所有关联表全部字段
        }
        List<TableSpec> result = new ArrayList<>();
        for (com.jdec.platform.common.api.dto.request.MenuTableSpec mSpec : menuTableSpecs) {
            if (mSpec == null) {
                continue;
            }
            Long modId =
                    resolveModuleIdForTable(
                            mSpec.getMenuId(),
                            mSpec.getTableName(),
                            rootModuleId,
                            primaryTable,
                            moduleTree);
            ModuleInfo mod =
                    (moduleTree != null && moduleTree.getModuleMap() != null)
                            ? moduleTree.getModuleMap().get(modId)
                            : null;
            String modPrimaryTable = (mod != null) ? mod.getPrimaryTable() : null;

            String rawTable = mSpec.getTableName() != null ? mSpec.getTableName().trim() : null;
            String table = rawTable;
            if (table == null || table.isBlank()) {
                table = modPrimaryTable;
            } else if (modPrimaryTable != null && !table.equalsIgnoreCase(modPrimaryTable)) {
                // 如果传入的物理表名不在整个模块树的任何模块主表或伴生表中，且当前模块有唯一明确的主表，自动校正为真实物理主表
                if (moduleTree != null && moduleTree.getModuleMap() != null) {
                    boolean tableExistsInTree =
                            moduleTree.getModuleMap().values().stream()
                                    .anyMatch(
                                            m ->
                                                    m != null
                                                            && rawTable.equalsIgnoreCase(
                                                                    m.getPrimaryTable()));
                    if (!tableExistsInTree) {
                        log.warn(
                                "MenuTableSpec 中指定的物理表名 [{}] 不在当前模块树的物理表中，根据模块 ID [{}] 自动校正为真实物理主表 [{}]",
                                rawTable,
                                modId,
                                modPrimaryTable);
                        table = modPrimaryTable;
                    }
                }
            }

            if (table == null || table.isBlank()) {
                continue;
            }

            result.add(
                    TableSpec.builder()
                            .moduleId(modId)
                            .tableName(table)
                            .columns(mSpec.getColumns())
                            .build());
        }
        return result;
    }

    private List<com.jdec.platform.data.api.dto.request.FilterItem> resolveFlatFilters(
            List<com.jdec.platform.common.api.dto.request.FlatFilterItem> flatFilters,
            Long rootModuleId,
            String primaryTable,
            ModuleTreeDTO moduleTree) {
        if (CollectionUtils.isEmpty(flatFilters)) {
            return new ArrayList<>();
        }
        List<com.jdec.platform.data.api.dto.request.FilterItem> result = new ArrayList<>();
        for (com.jdec.platform.common.api.dto.request.FlatFilterItem f : flatFilters) {
            if (f == null || f.getColumnName() == null || f.getColumnName().isBlank()) {
                continue;
            }
            String table =
                    (f.getTableName() != null && !f.getTableName().isBlank())
                            ? f.getTableName().trim()
                            : primaryTable;
            Long modId =
                    resolveModuleIdForTable(
                            f.getMenuId(), table, rootModuleId, primaryTable, moduleTree);
            result.add(
                    com.jdec.platform.data.api.dto.request.FilterItem.builder()
                            .moduleId(modId)
                            .tableName(table)
                            .columnName(f.getColumnName().trim())
                            .operator(f.getOperator() != null ? f.getOperator() : "EQ")
                            .value(f.getValue())
                            .build());
        }
        return result;
    }

    private List<com.jdec.platform.data.api.dto.request.SortItem> resolveFlatSorts(
            List<com.jdec.platform.common.api.dto.request.FlatSortItem> flatSorts,
            Long rootModuleId,
            String primaryTable,
            ModuleTreeDTO moduleTree) {
        if (CollectionUtils.isEmpty(flatSorts)) {
            return new ArrayList<>();
        }
        List<com.jdec.platform.data.api.dto.request.SortItem> result = new ArrayList<>();
        for (com.jdec.platform.common.api.dto.request.FlatSortItem s : flatSorts) {
            if (s == null || s.getColumnName() == null || s.getColumnName().isBlank()) {
                continue;
            }
            String table =
                    (s.getTableName() != null && !s.getTableName().isBlank())
                            ? s.getTableName().trim()
                            : primaryTable;
            Long modId =
                    resolveModuleIdForTable(
                            s.getMenuId(), table, rootModuleId, primaryTable, moduleTree);
            result.add(
                    com.jdec.platform.data.api.dto.request.SortItem.builder()
                            .moduleId(modId)
                            .tableName(table)
                            .columnName(s.getColumnName().trim())
                            .direction(s.getDirection() != null ? s.getDirection() : "DESC")
                            .build());
        }
        return result;
    }

    private Long resolveModuleIdForTable(
            Long specificMenuId,
            String tableName,
            Long rootModuleId,
            String primaryTable,
            ModuleTreeDTO moduleTree) {
        // 1. 若入参显式指定了靠近的菜单 ID，优先通过 openMenuApi 解析该菜单对应的模块 ID
        if (specificMenuId != null) {
            Long mappedModId = openMenuApi.getModuleIdByMenuId(specificMenuId);
            if (mappedModId != null
                    && moduleTree != null
                    && moduleTree.getModuleMap() != null
                    && moduleTree.getModuleMap().containsKey(mappedModId)) {
                return mappedModId;
            }
        }

        // 2. 若表名为主表，直接归属根模块
        if (tableName != null && tableName.equalsIgnoreCase(primaryTable)) {
            return rootModuleId;
        }

        // 3. 在当前模块树中根据 primaryTable 或字段表名查找匹配的模块
        if (moduleTree != null && moduleTree.getModuleMap() != null) {
            // 先尝试匹配作为主表的模块
            for (ModuleInfo m : moduleTree.getModuleMap().values()) {
                if (m != null
                        && tableName != null
                        && tableName.equalsIgnoreCase(m.getPrimaryTable())) {
                    return m.getModuleId();
                }
            }
            // 再尝试匹配字段包含该物理表名的模块
            if (moduleTree.getFieldMap() != null) {
                for (ModuleFieldDTO f : moduleTree.getFieldMap().values()) {
                    if (f != null
                            && tableName != null
                            && tableName.equalsIgnoreCase(f.getTableName())) {
                        Long modId = moduleTree.getModuleIdByFieldId(f.getId());
                        if (modId != null) {
                            return modId;
                        }
                    }
                }
            }
        }

        // 4. 默认兜底为根模块 ID
        return rootModuleId;
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
        return buildDataRecordItemFromDataRow(moduleId, menuId, dataRow, null);
    }

    private DataRecordItem buildDataRecordItemFromDataRow(
            Long moduleId, Long menuId, DataRow dataRow, Map<Long, Long> moduleToMenuMap) {
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
                            childItems.add(
                                    buildDataRecordItemFromDataRow(
                                            subModId, null, subRow, moduleToMenuMap));
                        }
                    }
                }

                // 解析子模块的 menuId：优先从 moduleToMenuMap 取，未命中则通过 openMenuApi 兜底查询
                Long subMenuId =
                        (moduleToMenuMap != null && subModId != null)
                                ? moduleToMenuMap.get(subModId)
                                : null;
                if (subMenuId == null && subModId != null) {
                    try {
                        subMenuId = openMenuApi.getMenuIdByModuleId(subModId);
                    } catch (Exception ex) {
                        log.debug("通过 openMenuApi 解析子模块 menuId 失败: subModId={}", subModId);
                    }
                }

                ModuleDataNode moduleDataNode =
                        ModuleDataNode.builder()
                                .moduleId(subModId)
                                .menuId(subMenuId)
                                .record(childItems)
                                .build();
                childrenNodes.add(moduleDataNode);
            }
        }

        // 当没有子模块记录或者为空列表时设置为 null，配合 @JsonInclude(NON_EMPTY) 彻底避免序列化为 "children": []
        List<ModuleDataNode> finalChildren = childrenNodes.isEmpty() ? null : childrenNodes;
        return DataRecordItem.builder().row(flatRow).children(finalChildren).build();
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

    @Override
    public List<MenuApprovalCountResp> getApprovalCountByMenu(Long menuId) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 通过 OpenMenuApi 获取菜单关联的根模块 ID，并抓取模块树
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);

        Long todoCount = 0L;
        Long doneCount = 0L;
        Long totalCount = 0L;

        // 2. 根据模块是否启用审批链分支统计
        if (approvalFilterResolver.isApprovalChainEnabled(rootModuleId)) {
            CommonFilterResolver.AllApprovalCountFilters countFilters =
                    approvalFilterResolver.resolveAllApprovalCountsFilters(
                            rootModuleId, moduleTree);

            // 待办 (type = 1)
            if (countFilters.todoFilter() != null
                    && !countFilters.todoFilter().shortCircuitEmpty()) {
                todoCount =
                        dataApi.count(
                                QueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(countFilters.todoFilter().filters())
                                        .build());
            }

            // 已办 (type = 2)
            if (countFilters.doneFilter() != null
                    && !countFilters.doneFilter().shortCircuitEmpty()) {
                doneCount =
                        dataApi.count(
                                QueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(countFilters.doneFilter().filters())
                                        .build());
            }

            // 全部 (type = 3)
            if (countFilters.allFilter() != null && !countFilters.allFilter().shortCircuitEmpty()) {
                totalCount =
                        dataApi.count(
                                QueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(countFilters.allFilter().filters())
                                        .build());
            }
        } else {
            // 未启用审批流：仅统计数据权限约束下的全部数据
            List<com.jdec.platform.data.api.dto.request.FilterItem> dataPermFilters =
                    approvalFilterResolver.buildDataPermissionFilters(moduleTree);
            totalCount =
                    dataApi.count(
                            QueryReq.builder()
                                    .moduleId(rootModuleId)
                                    .filters(dataPermFilters)
                                    .build());
        }

        return List.of(
                MenuApprovalCountResp.of("待办", 1, todoCount),
                MenuApprovalCountResp.of("已办", 2, doneCount),
                MenuApprovalCountResp.of("全部", 3, totalCount));
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
