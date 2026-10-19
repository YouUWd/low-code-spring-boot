package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.dto.ApprpvalIdButton;
import com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.FieldSortItem;
import com.jdec.platform.common.api.dto.request.MenuFlatFieldFilterItem;
import com.jdec.platform.common.api.dto.request.MenuListReq;
import com.jdec.platform.common.api.dto.request.MenuOptionReq;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.response.*;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
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

    // 标准业务操作按钮定义 (单例共享，避免每行重复分配 Map 对象)
    private static final List<Map<String, Object>> HANDLE_BUTTONS =
            List.of(Map.of("buttonKey", "handle", "buttonName", "办理"));
    private static final List<Map<String, Object>> VIEW_BUTTONS =
            List.of(Map.of("buttonKey", "view", "buttonName", "查阅"));

    private final UserMenuHeaderApi userMenuHeaderApi;
    private final OpenMenuApi openMenuApi;
    private final ConfigApi configApi;
    private final DataApi dataApi;
    private final CommonFilterResolver commonFilterResolver;
    private final SysUserApi sysUserApi;
    private final SysStatusApi sysStatusApi;
    private final IApprovalEngine approvalEngine;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;

    @Override
    public MenuDataResp queryByMenu(Long menuId, MenuQueryReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        long queryStart = System.currentTimeMillis();
        Long userId = getEffectiveUserId();
        Long roleId = UserContext.getRoleId();
        Integer approvalType = req != null ? req.getApprovalType() : null;
        int pageNo = (req != null && req.getPageNo() != null) ? req.getPageNo() : 1;
        int pageSize = (req != null && req.getPageSize() != null) ? req.getPageSize() : 20;

        log.info(
                "[PageQuery-Start] 接收菜单分页查询请求: menuId={}, pageNo={}, pageSize={}, approvalType={}, userId={}, roleId={}",
                menuId,
                pageNo,
                pageSize,
                approvalType,
                userId,
                roleId);

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
        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        Long currentUserId = getEffectiveUserId();

        log.info(
                "[PageQuery-Context] 菜单关联根模块及主表: menuId={}, rootModuleId={}, primaryTable={}, approvalEnabled={}",
                menuId,
                rootModuleId,
                primaryTable,
                approvalEnabled);

        CommonFilterResolver.PrimaryFilterResult filterResult =
                resolveFiltersForQuery(moduleTree, projectRelations, approvalType, approvalEnabled);

        if (filterResult.isEmpty()) {
            log.info(
                    "[PageQuery-ShortCircuit] 主表权限/审批过滤结果为空，短路直接返回空页面: menuId={}, rootModuleId={}, primaryTable={}",
                    menuId,
                    rootModuleId,
                    primaryTable);
            ModuleDataNode emptyNode =
                    ModuleDataNode.builder()
                            .moduleId(rootModuleId)
                            .menuId(menuId)
                            .total(0L)
                            .record(Collections.emptyList())
                            .build();

            List<ApprovalRowItem> emptyApprovalRows =
                    approvalEnabled ? Collections.emptyList() : null;

            return assembleMenuDataResp(
                    headerRoot, menuId, rootModuleId, roleId, emptyNode, emptyApprovalRows);
        }

        if (!filterResult.filters().isEmpty()) {
            finalFilters.addAll(filterResult.filters());
        }

        // 4. 调用底层低代码数据引擎进行分页查询
        long queryPageStart = System.currentTimeMillis();
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
        long queryPageCost = System.currentTimeMillis() - queryPageStart;

        // 5. 将 DataRow 数据集结构转换为自相似同构树节点 ModuleDataNode 与 ApprovalRowItem 列表
        Long total =
                (rawDataPage != null && rawDataPage.getTotal() != null)
                        ? rawDataPage.getTotal()
                        : 0L;
        int rowCount =
                (rawDataPage != null && rawDataPage.getRecords() != null)
                        ? rawDataPage.getRecords().size()
                        : 0;

        log.info(
                "[PageQuery-Execute] 底层低代码数据引擎分页查询完成: menuId={}, rootModuleId={}, 耗时={}ms, total={}, 当前页行数={}, pageNo={}, pageSize={}",
                menuId,
                rootModuleId,
                queryPageCost,
                total,
                rowCount,
                pageNo,
                pageSize);

        ModuleDataNode rootNode =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .total(total)
                        .record(new ArrayList<>())
                        .build();

        List<ApprovalRowItem> approvalRows = approvalEnabled ? new ArrayList<>() : null;

        if (rawDataPage != null && rawDataPage.getRecords() != null) {
            enhanceDataRows(rawDataPage.getRecords(), rootModuleId, primaryTable);

            // 若为全部(type=3)，预先拉取待办列表以准确区分每行是“办理”还是“查阅”
            Set<String> todoNos = null;
            Set<Long> todoIds = null;
            if (approvalEnabled && approvalType != null && approvalType == 3) {
                todoNos = new HashSet<>(getApprovalBusinessNos(rootModuleId, 1));
                todoIds = new HashSet<>(getApprovalRecordIds(rootModuleId, 1));
            }

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
                        List<Map<String, Object>> btns;
                        if (approvalType != null && approvalType == 1) {
                            // 待办全为办理
                            btns = HANDLE_BUTTONS;
                        } else if (approvalType != null && approvalType == 2) {
                            // 已办全为查阅
                            btns = VIEW_BUTTONS;
                        } else {
                            // 全部 (type=3) 或默认：
                            // 若记录在待办单据中，或者是由当前登录人创建的草稿 (business_status=1)，则为办理(handle)，否则为查阅(view)
                            boolean isTodoRecord =
                                    (businessNo != null
                                                    && todoNos != null
                                                    && todoNos.contains(businessNo))
                                            || (recordId != null
                                                    && todoIds != null
                                                    && todoIds.contains(recordId));
                            Object rawStatus = rawRow.getValue(primaryTable, "business_status");
                            Integer bStatus = null;
                            if (rawStatus instanceof Number) {
                                bStatus = ((Number) rawStatus).intValue();
                            } else if (rawStatus instanceof StatusSimpleResp) {
                                bStatus = ((StatusSimpleResp) rawStatus).getStatusValue();
                            }

                            Object rawCreator = rawRow.getValue(primaryTable, "created_by");
                            Long creator = null;
                            if (rawCreator instanceof Number) {
                                creator = ((Number) rawCreator).longValue();
                            } else if (rawCreator instanceof UserSimpleResp) {
                                creator = ((UserSimpleResp) rawCreator).getUserId();
                            }

                            boolean isMyDraft =
                                    creator != null
                                            && creator.equals(currentUserId)
                                            && bStatus != null
                                            && bStatus == 1;

                            btns = (isTodoRecord || isMyDraft) ? HANDLE_BUTTONS : VIEW_BUTTONS;
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
        log.info(
                "[PageQuery-Finish] 分页查询组装完成: menuId={}, rootModuleId={}, 总耗时={}ms, total={}, 返回记录数={}, 审批行数={}",
                menuId,
                rootModuleId,
                System.currentTimeMillis() - queryStart,
                total,
                rootNode.getRecord().size(),
                approvalRows != null ? approvalRows.size() : "null(未启用审批)");

        return assembleMenuDataResp(
                headerRoot, menuId, rootModuleId, roleId, rootNode, approvalRows);
    }

    @Override
    public ModuleDataNode queryList(MenuListReq req) {
        // 1. 根据 tables 中携带的 menuId 解析对应的 moduleId，在项目模块树中定位目标根模块树
        ModuleTreeDTO moduleTree = resolveRootModuleTree(req);
        Long rootModuleId = moduleTree.getRootModuleId();
        String primaryTable =
                (moduleTree.getRootModule() != null)
                        ? moduleTree.getRootModule().getPrimaryTable()
                        : null;

        // 收集请求中各模块 ID 对应的菜单 ID 映射关系，供装配节点 ModuleDataNode 时精准带出 menuId
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
        Long rootMenuId = moduleToMenuMap.get(rootModuleId);
        if (rootMenuId == null && rootModuleId != null) {
            try {
                rootMenuId = openMenuApi.getMenuIdByModuleId(rootModuleId);
                if (rootMenuId != null) {
                    moduleToMenuMap.put(rootModuleId, rootMenuId);
                }
            } catch (Exception ex) {
                log.debug("通过 openMenuApi 解析根模块 menuId 失败: rootModuleId={}", rootModuleId);
            }
        }

        // 解析本次请求的目标模块 (Target Module) 与目标菜单 (Target Menu)：
        // 遍历请求的 tables，选取在模块树中层级最高 (深度最小) 的模块作为返回数据的最顶层节点
        Long targetModuleId = rootModuleId;
        Long targetMenuId = rootMenuId;
        int minDepth = Integer.MAX_VALUE;
        if (req != null && !CollectionUtils.isEmpty(req.getTables())) {
            for (com.jdec.platform.common.api.dto.request.MenuTableSpec mSpec : req.getTables()) {
                if (mSpec == null) {
                    continue;
                }
                Long mId = null;
                if (mSpec.getMenuId() != null) {
                    mId = openMenuApi.getModuleIdByMenuId(mSpec.getMenuId());
                }
                if (mId == null) {
                    mId =
                            resolveModuleIdForTable(
                                    mSpec.getMenuId(),
                                    mSpec.getTableName(),
                                    rootModuleId,
                                    primaryTable,
                                    moduleTree);
                }
                if (mId != null) {
                    int depth = getModuleDepth(mId, rootModuleId, moduleTree);
                    if (depth < minDepth) {
                        minDepth = depth;
                        targetModuleId = mId;
                        targetMenuId = mSpec.getMenuId();
                    }
                }
            }
        }
        if (targetModuleId == null) {
            targetModuleId = rootModuleId;
        }
        if (targetMenuId == null) {
            targetMenuId = moduleToMenuMap.get(targetModuleId);
            if (targetMenuId == null && targetModuleId != null) {
                try {
                    targetMenuId = openMenuApi.getMenuIdByModuleId(targetModuleId);
                } catch (Exception ex) {
                    log.debug(
                            "通过 openMenuApi 解析 targetModuleId menuId 失败: targetModuleId={}",
                            targetModuleId);
                }
            }
        }

        // 2. 将 MenuTableSpec 转换为 data-api 物理投影表规格 TableSpec
        List<TableSpec> tableSpecs =
                resolveTableSpecs(
                        req != null ? req.getTables() : null,
                        rootModuleId,
                        primaryTable,
                        moduleTree);

        // 3. 构建 MenuTableSpec 允许的物理表名和字段集合映射：Map<小写表名, 允许的字段小写集合(null代表所有字段)>
        Map<String, Set<String>> requestedTableColumnsMap = null;
        if (req != null && !CollectionUtils.isEmpty(req.getTables())) {
            requestedTableColumnsMap = new HashMap<>();
            for (com.jdec.platform.common.api.dto.request.MenuTableSpec mSpec : req.getTables()) {
                if (mSpec == null
                        || mSpec.getTableName() == null
                        || mSpec.getTableName().isBlank()) {
                    continue;
                }
                String rawTable = mSpec.getTableName().trim().toLowerCase();
                Set<String> cols = null;
                if (mSpec.getColumns() != null
                        && !mSpec.getColumns().isEmpty()
                        && !mSpec.getColumns().contains("*")) {
                    cols = new HashSet<>();
                    for (String c : mSpec.getColumns()) {
                        if (c != null && !c.isBlank()) {
                            String cleanC =
                                    c.contains(".")
                                            ? c.substring(c.lastIndexOf('.') + 1).trim()
                                            : c.trim();
                            cols.add(cleanC.toLowerCase());
                        }
                    }
                }
                addTableSpecToMap(requestedTableColumnsMap, rawTable, cols);

                Long modId =
                        resolveModuleIdForTable(
                                mSpec.getMenuId(),
                                mSpec.getTableName(),
                                rootModuleId,
                                primaryTable,
                                moduleTree);
                if (moduleTree != null && moduleTree.getModuleMap() != null && modId != null) {
                    ModuleInfo mod = moduleTree.getModuleMap().get(modId);
                    if (mod != null
                            && mod.getPrimaryTable() != null
                            && !mod.getPrimaryTable().isBlank()) {
                        addTableSpecToMap(
                                requestedTableColumnsMap,
                                mod.getPrimaryTable().trim().toLowerCase(),
                                cols);
                    }
                }
            }
        }

        // 4. 将 FlatFilterItem 与 FlatSortItem 转换为 data-api 物理过滤与排序项 (自动解析消歧所属 moduleId)
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

        // 5. 审批流与数据权限过滤注入 (默认查询全部集合: 3)
        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        CommonFilterResolver.PrimaryFilterResult filterResult =
                resolveFiltersForQuery(moduleTree, projectRelations, 3, approvalEnabled);
        if (filterResult.isEmpty()) {
            return ModuleDataNode.builder()
                    .moduleId(targetModuleId)
                    .menuId(targetMenuId)
                    .total(0L)
                    .record(Collections.emptyList())
                    .build();
        }
        if (!filterResult.filters().isEmpty()) {
            finalFilters.addAll(filterResult.filters());
        }

        // 6. 阶段 1：轻量级调用底层数据引擎查询主表数据，收集符合条件的 business_no 集合
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
            return ModuleDataNode.builder()
                    .moduleId(targetModuleId)
                    .menuId(targetMenuId)
                    .total(0L)
                    .record(Collections.emptyList())
                    .build();
        }

        // 7. 阶段 2：通过数据引擎单表 API 在主表上精准查找每个 business_no 最新启用 (business_status = 99) 的最大版本主键 id 列表
        List<Long> latestEnabledIds = dataApi.getLatestVersionIds(primaryTable, businessNoSet, 99);

        if (CollectionUtils.isEmpty(latestEnabledIds)) {
            return ModuleDataNode.builder()
                    .moduleId(targetModuleId)
                    .menuId(targetMenuId)
                    .total(0L)
                    .record(Collections.emptyList())
                    .build();
        }

        // 8. 阶段 3：直接基于这批唯一的主键 id 列表，调用底层数据引擎进行全量关联投影查询
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

        // 9. 将筛选后的 DataRow 数据集结构转换为自相似同构树记录列表 (严格仅保留 MenuTableSpec 涉及的物理表、字段与子模块)
        List<DataRecordItem> recordList = new ArrayList<>();
        if (!finalRows.isEmpty()) {
            enhanceDataRows(finalRows, rootModuleId, primaryTable);
            for (DataRow rawRow : finalRows) {
                if (rawRow != null) {
                    recordList.add(
                            buildDataRecordItemFromDataRow(
                                    rootModuleId,
                                    rootMenuId,
                                    rawRow,
                                    moduleToMenuMap,
                                    requestedTableColumnsMap));
                }
            }
        }

        // 10. 若请求的目标模块是根模块，直接返回根模块节点；
        // 若请求的是子模块（如 menuId: 26, company_contact），则将树状结构提升至目标子模块层级，将所有根记录下的目标子模块记录平铺作为顶层返回
        List<DataRecordItem> finalRecords;
        if (targetModuleId.equals(rootModuleId)) {
            finalRecords = recordList;
        } else {
            finalRecords = extractTargetRecords(recordList, targetModuleId);
        }

        return ModuleDataNode.builder()
                .moduleId(targetModuleId)
                .menuId(targetMenuId)
                .total((long) finalRecords.size())
                .record(finalRecords)
                .build();
    }

    private int getModuleDepth(Long moduleId, Long rootModuleId, ModuleTreeDTO moduleTree) {
        if (moduleId == null || rootModuleId == null) {
            return Integer.MAX_VALUE;
        }
        if (moduleId.equals(rootModuleId)) {
            return 0;
        }
        if (moduleTree == null || moduleTree.getModuleMap() == null) {
            return Integer.MAX_VALUE;
        }
        int depth = 0;
        Long curr = moduleId;
        Set<Long> visited = new HashSet<>();
        while (curr != null && !curr.equals(rootModuleId)) {
            if (!visited.add(curr)) {
                return Integer.MAX_VALUE;
            }
            ModuleInfo info = moduleTree.getModuleMap().get(curr);
            if (info == null || info.getParentId() == null || info.getParentId() <= 0) {
                return Integer.MAX_VALUE;
            }
            curr = info.getParentId();
            depth++;
        }
        return curr != null && curr.equals(rootModuleId) ? depth : Integer.MAX_VALUE;
    }

    private List<DataRecordItem> extractTargetRecords(
            List<DataRecordItem> rootRecords, Long targetModuleId) {
        List<DataRecordItem> targetItems = new ArrayList<>();
        if (CollectionUtils.isEmpty(rootRecords) || targetModuleId == null) {
            return targetItems;
        }
        for (DataRecordItem item : rootRecords) {
            if (item == null || item.getChildren() == null) {
                continue;
            }
            collectRecordsFromChildren(item.getChildren(), targetModuleId, targetItems);
        }
        return targetItems;
    }

    private void collectRecordsFromChildren(
            List<ModuleDataNode> children, Long targetModuleId, List<DataRecordItem> targetItems) {
        if (CollectionUtils.isEmpty(children) || targetModuleId == null) {
            return;
        }
        for (ModuleDataNode node : children) {
            if (node == null) {
                continue;
            }
            if (targetModuleId.equals(node.getModuleId())) {
                if (node.getRecord() != null) {
                    targetItems.addAll(node.getRecord());
                }
            } else if (node.getRecord() != null) {
                for (DataRecordItem subItem : node.getRecord()) {
                    if (subItem != null && subItem.getChildren() != null) {
                        collectRecordsFromChildren(
                                subItem.getChildren(), targetModuleId, targetItems);
                    }
                }
            }
        }
    }

    private void addTableSpecToMap(Map<String, Set<String>> map, String table, Set<String> cols) {
        if (table == null || table.isBlank()) {
            return;
        }
        String key = table.trim().toLowerCase();
        if (!map.containsKey(key)) {
            map.put(key, cols != null ? new HashSet<>(cols) : null);
        } else {
            Set<String> existing = map.get(key);
            if (existing != null) {
                if (cols == null) {
                    map.put(key, null);
                } else {
                    existing.addAll(cols);
                }
            }
        }
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

        // 3. 审批流与数据权限过滤注入 (1-待办, 2-已办, 3-全部)
        Integer approvalType = req.getApprovalType();
        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        CommonFilterResolver.PrimaryFilterResult filterResult =
                resolveFiltersForQuery(moduleTree, projectRelations, approvalType, approvalEnabled);

        if (filterResult.isEmpty()) {
            // 审批过滤无候选匹配时短路直接返回空候选项列表
            return Collections.emptyList();
        }

        if (!filterResult.filters().isEmpty()) {
            finalFilters.addAll(filterResult.filters());
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
        return buildDataRecordItemFromDataRow(moduleId, menuId, dataRow, null, null);
    }

    private DataRecordItem buildDataRecordItemFromDataRow(
            Long moduleId, Long menuId, DataRow dataRow, Map<Long, Long> moduleToMenuMap) {
        return buildDataRecordItemFromDataRow(moduleId, menuId, dataRow, moduleToMenuMap, null);
    }

    private DataRecordItem buildDataRecordItemFromDataRow(
            Long moduleId,
            Long menuId,
            DataRow dataRow,
            Map<Long, Long> moduleToMenuMap,
            Map<String, Set<String>> requestedTableColumnsMap) {
        if (dataRow == null) {
            return DataRecordItem.builder().row(Collections.emptyMap()).build();
        }

        Map<String, Object> flatRow = new LinkedHashMap<>();
        List<ModuleDataNode> childrenNodes = new ArrayList<>();

        // 1. 展平当前数据行的物理表字段 (通过 DataRow.getTable 获取表结构化映射)
        if (dataRow.getTables() != null) {
            for (String tableName : dataRow.getTables().keySet()) {
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                String normalizedTable = tableName.trim().toLowerCase();
                // 若指定了投影表，且当前表不在请求的物理表列表中，则跳过无关表
                if (requestedTableColumnsMap != null
                        && !requestedTableColumnsMap.containsKey(normalizedTable)) {
                    continue;
                }
                Set<String> allowedCols =
                        requestedTableColumnsMap != null
                                ? requestedTableColumnsMap.get(normalizedTable)
                                : null;

                Map<String, Object> tableData = dataRow.getTable(tableName);
                if (tableData != null) {
                    for (Map.Entry<String, Object> colEntry : tableData.entrySet()) {
                        if (colEntry.getKey() != null && !colEntry.getKey().isBlank()) {
                            String rawCol = colEntry.getKey().trim();
                            String cleanCol =
                                    rawCol.contains(".")
                                            ? rawCol.substring(rawCol.lastIndexOf('.') + 1).trim()
                                            : rawCol;
                            // 若指定了列范围，且当前列不在列投影集合中，则跳过无关列
                            if (allowedCols != null
                                    && !allowedCols.contains(cleanCol.toLowerCase())) {
                                continue;
                            }
                            flatRow.put(tableName + "." + cleanCol, colEntry.getValue());
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
                            DataRecordItem childItem =
                                    buildDataRecordItemFromDataRow(
                                            subModId,
                                            null,
                                            subRow,
                                            moduleToMenuMap,
                                            requestedTableColumnsMap);
                            // 仅当子记录项包含有效数据(有行数据或有级联子节点)时才加入
                            if ((childItem.getRow() != null && !childItem.getRow().isEmpty())
                                    || (childItem.getChildren() != null
                                            && !childItem.getChildren().isEmpty())) {
                                childItems.add(childItem);
                            }
                        }
                    }
                }

                // 如果经过过滤后该子模块没有任何有效记录项，跳过该子模块节点
                if (childItems.isEmpty()) {
                    continue;
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
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());

        Long todoCount = 0L;
        Long doneCount = 0L;
        Long totalCount = 0L;

        // 2. 根据模块是否启用审批链分支统计
        String primaryTable =
                (moduleTree != null && moduleTree.getRootModule() != null)
                        ? moduleTree.getRootModule().getPrimaryTable()
                        : null;
        long countStart = System.currentTimeMillis();
        Long currentUserId = getEffectiveUserId();

        log.info(
                "[ApprovalCount-Start] 开始统计菜单审批角标: menuId={}, rootModuleId={}, primaryTable={}, currentUserId={}",
                menuId,
                rootModuleId,
                primaryTable,
                currentUserId);

        if (isApprovalChainEnabled(rootModuleId)) {
            ApprovalIdButtonListResp resp = null;
            try {
                resp = approvalEngine.getApprovalListButton(rootModuleId);
            } catch (Exception ex) {
                log.warn(
                        "[ApprovalCount] 统计审批角标获取审批单据失败: rootModuleId={}, err={}",
                        rootModuleId,
                        ex.getMessage());
            }

            List<String> todoNos =
                    resp != null ? extractBusinessNos(resp.getToDoList()) : Collections.emptyList();
            List<String> doneNos =
                    resp != null ? extractBusinessNos(resp.getDoneList()) : Collections.emptyList();
            List<String> allNos =
                    resp != null ? extractBusinessNos(resp.getAllList()) : Collections.emptyList();

            log.info(
                    "[ApprovalCount] 审批引擎原始单据数据: rootModuleId={}, 待办单据数={}, 已办单据数={}, 全部单据数={}",
                    rootModuleId,
                    todoNos.size(),
                    doneNos.size(),
                    allNos.size());

            // 待办 (type = 1): [审批待办] OR [当前人草稿 (business_status = 1)]
            CommonFilterResolver.PrimaryFilterResult todoResult =
                    commonFilterResolver.resolveTodoFilters(moduleTree, projectRelations, todoNos);
            if (!todoResult.isEmpty()) {
                todoCount =
                        dataApi.count(
                                QueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(todoResult.filters())
                                        .build());
            }
            log.info(
                    "[ApprovalCount-Todo] 待办角标统计完成: rootModuleId={}, 匹配主表ID数={}, 最终count={}",
                    rootModuleId,
                    todoResult.matchedIds().size(),
                    todoCount);

            // 已办 (type = 2): 仅已办单据
            if (!doneNos.isEmpty()) {
                CommonFilterResolver.PrimaryFilterResult doneResult =
                        commonFilterResolver.resolveDoneFilters(moduleTree, doneNos);
                if (!doneResult.isEmpty()) {
                    doneCount =
                            dataApi.count(
                                    QueryReq.builder()
                                            .moduleId(rootModuleId)
                                            .filters(doneResult.filters())
                                            .build());
                }
            }
            log.info(
                    "[ApprovalCount-Done] 已办角标统计完成: rootModuleId={}, 已办单据数={}, 最终count={}",
                    rootModuleId,
                    doneNos.size(),
                    doneCount);

            // 全部 (type = 3): [审批全部] OR [当前人单据] OR [数据权限]
            CommonFilterResolver.PrimaryFilterResult allResult =
                    commonFilterResolver.resolveAllFilters(moduleTree, projectRelations, allNos);
            if (!allResult.isEmpty()) {
                totalCount =
                        dataApi.count(
                                QueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(allResult.filters())
                                        .build());
            }
            log.info(
                    "[ApprovalCount-All] 全部角标统计完成: rootModuleId={}, 匹配主表ID数={}, 最终count={}",
                    rootModuleId,
                    allResult.matchedIds().size(),
                    totalCount);
        } else {
            // 未启用审批流：[当前人单据] OR [数据权限]
            log.info(
                    "[ApprovalCount-NoApproval] 模块未启用审批流，仅统计个人创建及数据权限: rootModuleId={}, primaryTable={}, currentUserId={}",
                    rootModuleId,
                    primaryTable,
                    currentUserId);
            CommonFilterResolver.PrimaryFilterResult noApprovalResult =
                    commonFilterResolver.resolveDefaultFilters(moduleTree, projectRelations);
            if (!noApprovalResult.isEmpty()) {
                totalCount =
                        dataApi.count(
                                QueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(noApprovalResult.filters())
                                        .build());
            }
            log.info(
                    "[ApprovalCount-NoApproval] 全部角标统计完成: rootModuleId={}, 匹配主表ID数={}, 最终count={}",
                    rootModuleId,
                    noApprovalResult.matchedIds().size(),
                    totalCount);
        }

        long cost = System.currentTimeMillis() - countStart;
        log.info(
                "[ApprovalCount-Finish] 菜单审批角标统计完成: menuId={}, rootModuleId={}, 耗时={}ms, 统计结果=[待办:{}, 已办:{}, 全部:{}]",
                menuId,
                rootModuleId,
                cost,
                todoCount,
                doneCount,
                totalCount);

        return List.of(
                MenuApprovalCountResp.of("待办", 1, todoCount),
                MenuApprovalCountResp.of("已办", 2, doneCount),
                MenuApprovalCountResp.of("全部", 3, totalCount));
    }

    // ==================== 审批流与权限业务编排辅助逻辑 ====================

    /** 检查根模块是否启用了审批流 */
    public boolean isApprovalChainEnabled(Long rootModuleId) {
        if (rootModuleId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(
                    sysApprovalChainConfigApi.getApprovalStatus(
                            Collections.singletonList(rootModuleId)));
        } catch (Exception ex) {
            log.warn("查询模块审批链状态失败: rootModuleId={}, err={}", rootModuleId, ex.getMessage());
            return false;
        }
    }

    /** 业务编排：根据 moduleTree 的审批链状态及请求的 approvalType，决策调用语义化的 CommonFilterResolver 方法 */
    private CommonFilterResolver.PrimaryFilterResult resolveFiltersForQuery(
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Integer approvalType,
            boolean approvalEnabled) {

        Long rootModuleId = moduleTree != null ? moduleTree.getRootModuleId() : null;
        String primaryTable =
                (moduleTree != null && moduleTree.getRootModule() != null)
                        ? moduleTree.getRootModule().getPrimaryTable()
                        : null;

        log.info(
                "[PageQuery-Filter] 开始解析分页过滤条件: rootModuleId={}, primaryTable={}, approvalType={}, approvalEnabled={}",
                rootModuleId,
                primaryTable,
                approvalType,
                approvalEnabled);

        // 1. 无审批链或未指定审批类型：默认无审批流场景 (个人单据 + 行级数据权限)
        if (!approvalEnabled || approvalType == null) {
            return commonFilterResolver.resolveDefaultFilters(moduleTree, projectRelations);
        }

        // 2. 有审批链：按审批类型拉取审批引擎单据号
        List<String> approvalNos = getApprovalBusinessNos(rootModuleId, approvalType);
        log.info(
                "[PageQuery-Filter] 从审批引擎获取单据完成: rootModuleId={}, approvalType={}, 单据数={}, 单据样例={}",
                rootModuleId,
                approvalType,
                approvalNos.size(),
                CommonFilterResolver.formatSample(approvalNos, 5));

        // 2.1 已办 (type = 2)：仅已办单据
        if (approvalType == 2) {
            if (approvalNos.isEmpty()) {
                log.info(
                        "[PageQuery-Filter] 已办类型且审批引擎未返回任何单据，短路返回空过滤结果: rootModuleId={}, approvalType=2",
                        rootModuleId);
                return CommonFilterResolver.PrimaryFilterResult.empty();
            }
            return commonFilterResolver.resolveDoneFilters(moduleTree, approvalNos);
        }

        // 2.2 待办 (type = 1)：[审批待办] OR [当前人草稿 (business_status = 1)]
        if (approvalType == 1) {
            return commonFilterResolver.resolveTodoFilters(
                    moduleTree, projectRelations, approvalNos);
        }

        // 2.3 全部 (type = 3)：[审批全部] OR [当前人单据 (不限状态)] OR [行级数据权限]
        return commonFilterResolver.resolveAllFilters(moduleTree, projectRelations, approvalNos);
    }

    /** 从审批引擎获取指定审批类型的单据编号集合 */
    private List<String> getApprovalBusinessNos(Long rootModuleId, Integer approvalType) {
        if (rootModuleId == null || approvalType == null) {
            return Collections.emptyList();
        }
        try {
            ApprovalIdButtonListResp resp = approvalEngine.getApprovalListButton(rootModuleId);
            if (resp == null) {
                return Collections.emptyList();
            }
            List<ApprpvalIdButton> list =
                    switch (approvalType) {
                        case 1 -> resp.getToDoList();
                        case 2 -> resp.getDoneList();
                        default -> resp.getAllList();
                    };
            return extractBusinessNos(list);
        } catch (Exception ex) {
            log.warn(
                    "获取审批单据失败: rootModuleId={}, type={}, err={}",
                    rootModuleId,
                    approvalType,
                    ex.getMessage());
            return Collections.emptyList();
        }
    }

    /** 从审批引擎获取指定审批类型的物理记录 ID 集合 (用于辅助按钮决策) */
    private List<Long> getApprovalRecordIds(Long rootModuleId, Integer approvalType) {
        if (rootModuleId == null || approvalType == null) {
            return Collections.emptyList();
        }
        try {
            ApprovalIdButtonListResp resp = approvalEngine.getApprovalListButton(rootModuleId);
            if (resp == null) {
                return Collections.emptyList();
            }
            List<ApprpvalIdButton> list =
                    switch (approvalType) {
                        case 1 -> resp.getToDoList();
                        case 2 -> resp.getDoneList();
                        default -> resp.getAllList();
                    };
            if (CollectionUtils.isEmpty(list)) {
                return Collections.emptyList();
            }
            return list.stream()
                    .map(ApprpvalIdButton::getRecordId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    /** 提取审批单据对象中的 business_no 列表 */
    private List<String> extractBusinessNos(List<ApprpvalIdButton> list) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(ApprpvalIdButton::getBusinessNo)
                .filter(no -> no != null && !no.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
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
