package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.resp.ApprovalBusinessNoResp;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.MenuActiveToggleReq;
import com.jdec.platform.common.api.dto.request.MenuChangeQueryReq;
import com.jdec.platform.common.api.dto.request.MenuListReq;
import com.jdec.platform.common.api.dto.request.MenuOptionReq;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.request.MenuTableSpec;
import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ActionRowItem;
import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.FieldMetaDTO;
import com.jdec.platform.common.api.dto.response.MenuApprovalCountResp;
import com.jdec.platform.common.api.dto.response.MenuChangeCountResp;
import com.jdec.platform.common.api.dto.response.MenuDataResp;
import com.jdec.platform.common.api.dto.response.MenuDetailResp;
import com.jdec.platform.common.api.dto.response.ModuleDataNode;
import com.jdec.platform.common.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.biz.action.MenuActionBundle;
import com.jdec.platform.common.biz.action.MenuActionButtonService;
import com.jdec.platform.common.biz.action.MenuActionContext;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.common.biz.service.QueryResultEnhanceService;
import com.jdec.platform.common.biz.service.impl.CommonFilterResolver.FilterSpec;
import com.jdec.platform.common.biz.service.impl.CommonFilterResolver.PrimaryFilterResult;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SingleTableQueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * 公共业务聚合服务实现 (基于菜单的数据隔离、动态列表与详情聚合)
 *
 * <p>Service 层仅负责跨服务业务编排（表头配置、模块树元数据、系统级过滤组合决策、数据引擎查询与字典增强），底层逻辑均已下沉至 {@link CommonFilterResolver}、
 * {@link ApprovalConditionResolver}、{@link MenuActionButtonService}、{@link PhysicalQueryMapper} 与
 * {@link QueryResultEnhanceService}。
 */
@Slf4j
@Service
@AllArgsConstructor
public class CommonAggregateServiceImpl implements CommonAggregateService {

    private static final int ENABLED_BUSINESS_STATUS = 99;

    private final UserMenuHeaderApi userMenuHeaderApi;
    private final OpenMenuApi openMenuApi;
    private final ConfigApi configApi;
    private final DataApi dataApi;
    private final CommonFilterResolver commonFilterResolver;
    private final IApprovalEngine approvalEngine;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final QueryResultEnhanceService queryResultEnhanceService;
    private final MenuActionButtonService menuActionButtonService;
    private final JooqDataContextFactory jooqDataContextFactory;
    private final SysStatusApi sysStatusApi;

    // ==================== 1. 核心公开业务接口实现 ====================

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

        MenuHeaderColumnResp headerRoot = getHeaderDisplayOrThrow(userId, menuId, roleId);
        Long rootModuleId = headerRoot.getModuleId();
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;

        List<Long> checkedFieldIds =
                PhysicalQueryMapper.collectAndMaskHeaderFields(
                        headerRoot, req, moduleTree, projectRelations);
        List<TableSpec> tableSpecs = PhysicalQueryMapper.toTableSpecs(moduleTree, checkedFieldIds);
        List<FilterItem> finalFilters =
                req != null
                        ? PhysicalQueryMapper.toTableFilters(moduleTree, req.getFilters())
                        : new ArrayList<>();
        List<SortItem> finalSorts =
                req != null
                        ? PhysicalQueryMapper.toTableSorts(moduleTree, req.getSorts())
                        : new ArrayList<>();

        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        List<ApprovalBusinessNoResp> approvalResp =
                approvalEnabled ? fetchApprovalBusinessNos(rootModuleId) : null;
        ModulePermissionDTO rootModulePerm = fetchModulePermission(rootModuleId, roleId);
        boolean hasEditPermission = hasModuleEditPermission(rootModulePerm);

        PrimaryFilterResult filterResult =
                resolveSystemFilters(
                        moduleTree,
                        buildSystemFilterSpec(
                                approvalType,
                                approvalEnabled,
                                approvalResp,
                                projectRelations,
                                hasEditPermission));

        ModuleInfo rootModuleInfo = moduleTree != null ? moduleTree.getModule(rootModuleId) : null;
        int enableActive =
                (rootModuleInfo != null && rootModuleInfo.getEnableActive() != null)
                        ? rootModuleInfo.getEnableActive()
                        : 0;
        int enableChange =
                (rootModuleInfo != null && rootModuleInfo.getEnableChange() != null)
                        ? rootModuleInfo.getEnableChange()
                        : 0;

        MenuActionContext actionCtx =
                MenuActionContext.builder()
                        .projectNo(AppContext.getProjectNo())
                        .rootModuleId(rootModuleId)
                        .moduleCode(rootModuleInfo != null ? rootModuleInfo.getModuleCode() : null)
                        .primaryTable(primaryTable)
                        .enableActive(enableActive)
                        .enableChange(enableChange)
                        .userId(userId)
                        .roleId(roleId)
                        .hasEditPermission(hasEditPermission)
                        .build();

        if (filterResult.isEmpty()) {
            log.info(
                    "[PageQuery-ShortCircuit] 主表权限/审批过滤结果为空，直接返回空页: menuId={}, rootModuleId={}",
                    menuId,
                    rootModuleId);
            MenuActionBundle emptyActions =
                    menuActionButtonService != null
                            ? menuActionButtonService.buildEmptyActions(actionCtx)
                            : MenuActionBundle.none();
            return assembleMenuDataResp(
                    headerRoot,
                    rootModulePerm,
                    buildEmptyModuleDataNode(rootModuleId, menuId),
                    emptyActions.getGlobalActions(),
                    approvalEnabled ? Collections.emptyList() : null,
                    emptyActions.getCustomActions(),
                    emptyActions.getInteractionActions());
        }

        finalFilters.addAll(filterResult.filters());

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

        long total =
                (rawDataPage != null && rawDataPage.getTotal() != null)
                        ? rawDataPage.getTotal()
                        : 0L;
        List<DataRow> rawRecords =
                (rawDataPage != null && rawDataPage.getRecords() != null)
                        ? rawDataPage.getRecords()
                        : Collections.emptyList();

        List<DataRecordItem> recordItems =
                enhanceAndConvertRecords(rawRecords, rootModuleId, primaryTable, headerRoot);
        List<ActionRowItem> approvalRows =
                approvalEnabled
                        ? ApprovalConditionResolver.buildApprovalRows(
                                rawRecords, primaryTable, approvalType, approvalResp, userId)
                        : null;
        MenuActionBundle actions =
                menuActionButtonService != null
                        ? menuActionButtonService.buildActions(rawRecords, actionCtx)
                        : MenuActionBundle.none();

        ModuleDataNode rootNode =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .total(total)
                        .record(recordItems)
                        .build();

        log.info(
                "[PageQuery-Finish] 分页查询组装完成: menuId={}, rootModuleId={}, 总耗时={}ms, total={}, 返回记录数={}",
                menuId,
                rootModuleId,
                System.currentTimeMillis() - queryStart,
                total,
                recordItems.size());

        return assembleMenuDataResp(
                headerRoot,
                rootModulePerm,
                rootNode,
                actions.getGlobalActions(),
                approvalRows,
                actions.getCustomActions(),
                actions.getInteractionActions());
    }

    @Override
    public MenuDataResp queryChangeListByMenu(Long menuId, MenuChangeQueryReq req) {
        if (menuId == null) {
            throw new BusinessException("菜单 ID 不能为空");
        }

        long queryStart = System.currentTimeMillis();
        Long userId = getEffectiveUserId();
        Long roleId = UserContext.getRoleId();
        int pageNo =
                (req != null && req.getPageNo() != null && req.getPageNo() > 0)
                        ? req.getPageNo()
                        : 1;
        int pageSize =
                (req != null && req.getPageSize() != null && req.getPageSize() > 0)
                        ? req.getPageSize()
                        : 20;

        log.info(
                "[ChangeQuery-Start] 接收变更单列表查询: menuId={}, pageNo={}, pageSize={}, readStatus={}, userId={}, roleId={}",
                menuId,
                pageNo,
                pageSize,
                req != null ? req.getReadStatus() : null,
                userId,
                roleId);

        MenuHeaderColumnResp headerRoot = getHeaderDisplayOrThrow(userId, menuId, roleId);
        Long rootModuleId = headerRoot.getModuleId();
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;
        if (primaryTable == null || primaryTable.isBlank()) {
            throw new BusinessException("原单根模块主表未配置，无法推导变更表名: moduleId=" + rootModuleId);
        }

        PhysicalQueryMapper.ensureChangeColumnsInHeader(headerRoot, primaryTable, rootModuleId);
        MenuHeaderColumnResp prunedHeader = PhysicalQueryMapper.pruneHeaderToFirstLevel(headerRoot);

        String changeTable = primaryTable + "_change";
        List<String> targetColumns =
                PhysicalQueryMapper.collectChangeTargetColumns(
                        prunedHeader, moduleTree, primaryTable);
        List<FilterItem> singleFilters =
                PhysicalQueryMapper.buildChangeFilters(req, moduleTree, primaryTable, changeTable);
        List<SortItem> singleSorts =
                PhysicalQueryMapper.buildChangeSorts(req, moduleTree, primaryTable);

        DataPage<List<Map<String, Object>>> pageResult =
                dataApi.querySingleTablePage(
                        SingleTableQueryReq.builder()
                                .tableName(changeTable)
                                .columns(targetColumns)
                                .filters(singleFilters)
                                .sorts(singleSorts)
                                .pageNo(pageNo)
                                .pageSize(pageSize)
                                .build());

        long total =
                (pageResult != null && pageResult.getTotal() != null) ? pageResult.getTotal() : 0L;
        List<DataRow> rawRecords = PhysicalQueryMapper.toChangeDataRows(pageResult, primaryTable);
        List<DataRecordItem> recordItems =
                enhanceAndConvertRecords(rawRecords, rootModuleId, primaryTable, prunedHeader);

        ModuleDataNode rootNode =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .total(total)
                        .record(recordItems)
                        .build();

        log.info(
                "[ChangeQuery-Finish] 变更单单表分页查询组装完成: menuId={}, changeTable={}, primaryTable={}, 总耗时={}ms, total={}, 返回记录数={}",
                menuId,
                changeTable,
                primaryTable,
                System.currentTimeMillis() - queryStart,
                total,
                recordItems.size());

        return assembleMenuDataResp(prunedHeader, null, rootNode, null, null, null, null);
    }

    @Override
    public ModuleDataNode queryList(MenuListReq req) {
        ModuleTreeDTO moduleTree = resolveRootModuleTree(req);
        Long rootModuleId = moduleTree.getRootModuleId();
        String primaryTable = moduleTree.getPrimaryTable();

        Map<Long, Long> moduleToMenuMap = buildModuleToMenuMap(req, rootModuleId);
        Long rootMenuId = moduleToMenuMap.get(rootModuleId);
        TargetModuleContext targetCtx =
                resolveTargetModuleContext(
                        req, rootModuleId, rootMenuId, moduleTree, moduleToMenuMap);

        List<MenuTableSpec> reqTables = req != null ? req.getTables() : null;
        List<TableSpec> tableSpecs =
                PhysicalQueryMapper.toTableSpecs(
                        reqTables, moduleTree, openMenuApi::getModuleIdByMenuId);
        Map<String, Set<String>> requestedTableColumnsMap =
                PhysicalQueryMapper.buildRequestedTableColumnsMap(
                        reqTables, moduleTree, openMenuApi::getModuleIdByMenuId);
        List<FilterItem> finalFilters =
                PhysicalQueryMapper.toFlatFilters(
                        req != null ? req.getFilters() : null,
                        moduleTree,
                        openMenuApi::getModuleIdByMenuId);
        List<SortItem> finalSorts =
                PhysicalQueryMapper.toFlatSorts(
                        req != null ? req.getSorts() : null,
                        moduleTree,
                        openMenuApi::getModuleIdByMenuId);

        Long roleId = UserContext.getRoleId();
        PrimaryFilterResult filterResult =
                resolveMenuSystemFilters(moduleTree, rootModuleId, roleId, 3);
        if (filterResult.isEmpty()) {
            return buildEmptyModuleDataNode(targetCtx.targetModuleId(), targetCtx.targetMenuId());
        }
        finalFilters.addAll(filterResult.filters());

        List<Long> latestEnabledIds =
                fetchLatestEnabledVersionIds(rootModuleId, primaryTable, finalFilters, finalSorts);
        if (CollectionUtils.isEmpty(latestEnabledIds)) {
            return buildEmptyModuleDataNode(targetCtx.targetModuleId(), targetCtx.targetMenuId());
        }

        FilterItem idInFilter = FilterItem.in(primaryTable, "id", latestEnabledIds);
        idInFilter.setModuleId(rootModuleId);

        DataBlock enabledBlock =
                dataApi.queryList(
                        QueryReq.builder()
                                .moduleId(rootModuleId)
                                .tables(tableSpecs)
                                .filters(List.of(idInFilter))
                                .sorts(finalSorts)
                                .build());

        List<DataRow> finalRows =
                (enabledBlock != null && enabledBlock.getRecords() != null)
                        ? enabledBlock.getRecords()
                        : Collections.emptyList();

        List<DataRecordItem> recordList = new ArrayList<>(finalRows.size());
        if (!finalRows.isEmpty()) {
            MenuHeaderColumnResp headerRoot = queryHeaderRootQuietly(rootMenuId, roleId);
            queryResultEnhanceService.enhanceDataRows(
                    finalRows, rootModuleId, primaryTable, headerRoot);
            for (DataRow rawRow : finalRows) {
                if (rawRow != null) {
                    recordList.add(
                            PhysicalQueryMapper.toDataRecordItem(
                                    rawRow,
                                    moduleToMenuMap,
                                    requestedTableColumnsMap,
                                    this::queryMenuIdQuietly));
                }
            }
        }

        List<DataRecordItem> finalRecords =
                targetCtx.targetModuleId().equals(rootModuleId)
                        ? recordList
                        : PhysicalQueryMapper.extractTargetRecords(
                                recordList, targetCtx.targetModuleId());

        return ModuleDataNode.builder()
                .moduleId(targetCtx.targetModuleId())
                .menuId(targetCtx.targetMenuId())
                .total((long) finalRecords.size())
                .record(finalRecords)
                .build();
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

        MenuHeaderColumnResp headerRoot = getHeaderDisplayOrThrow(userId, menuId, roleId);
        Long rootModuleId = headerRoot.getModuleId();
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);

        List<Long> checkedFieldIds = PhysicalQueryMapper.collectCheckedFieldIds(headerRoot);
        List<TableSpec> tableSpecs = PhysicalQueryMapper.toTableSpecs(moduleTree, checkedFieldIds);

        DataBlock dataBlock =
                dataApi.getDetail(
                        DetailQueryReq.builder()
                                .moduleId(rootModuleId)
                                .id(id)
                                .tables(tableSpecs)
                                .build());

        ModuleDataNode dataTree =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .record(new ArrayList<>())
                        .build();

        if (dataBlock != null && dataBlock.hasRecords()) {
            String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;
            queryResultEnhanceService.enhanceDataRows(
                    dataBlock.getRecords(), rootModuleId, primaryTable, headerRoot);
            DataRow rawRow = dataBlock.getFirstRecord();
            if (rawRow != null) {
                dataTree.getRecord().add(PhysicalQueryMapper.toDataRecordItem(rawRow));
            }
        }

        return MenuDetailResp.builder()
                .meta(buildModuleMetaList(moduleTree, rootModuleId, menuId, roleId))
                .data(dataTree)
                .build();
    }

    @Override
    public List<DataOptionResp> getOptionsByMenu(Long menuId, MenuOptionReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (req == null || req.getFieldId() == null) {
            throw new IllegalArgumentException("目标字段 ID (fieldId) 不能为空");
        }

        Long rootModuleId = getModuleIdByMenuOrThrow(menuId);
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        PhysicalQueryMapper.PhysicalFieldRef targetField =
                PhysicalQueryMapper.resolve(moduleTree, req.getFieldId());

        List<FilterItem> finalFilters =
                PhysicalQueryMapper.toTableFilters(moduleTree, req.getFilters());
        Long roleId = UserContext.getRoleId();

        PrimaryFilterResult filterResult =
                resolveMenuSystemFilters(moduleTree, rootModuleId, roleId, req.getApprovalType());
        if (filterResult.isEmpty()) {
            return Collections.emptyList();
        }
        finalFilters.addAll(filterResult.filters());

        List<DataOptionResp> rawOptions =
                dataApi.getOptions(
                        OptionReq.builder()
                                .moduleId(rootModuleId)
                                .tableName(targetField.tableName())
                                .columnName(targetField.columnName())
                                .filters(finalFilters)
                                .direction(req.getDirection())
                                .build());

        MenuHeaderColumnResp headerRoot = queryHeaderRootQuietly(menuId, roleId);
        return queryResultEnhanceService.enhanceOptions(
                rawOptions, targetField.columnName(), rootModuleId, headerRoot, req.getFieldId());
    }

    @Override
    public List<MenuApprovalCountResp> getApprovalCountByMenu(Long menuId) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        Long rootModuleId = getModuleIdByMenuOrThrow(menuId);
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        boolean hasEditPermission =
                hasModuleEditPermission(
                        fetchModulePermission(rootModuleId, UserContext.getRoleId()));

        long countStart = System.currentTimeMillis();
        long todoCount = 0L;
        long doneCount = 0L;
        long totalCount;

        if (isApprovalChainEnabled(rootModuleId)) {
            List<ApprovalBusinessNoResp> resp = fetchApprovalBusinessNos(rootModuleId);
            todoCount =
                    countByFilterSpec(
                            rootModuleId,
                            moduleTree,
                            buildSystemFilterSpec(
                                    1, true, resp, projectRelations, hasEditPermission));
            doneCount =
                    countByFilterSpec(
                            rootModuleId,
                            moduleTree,
                            buildSystemFilterSpec(
                                    2, true, resp, projectRelations, hasEditPermission));
            totalCount =
                    countByFilterSpec(
                            rootModuleId,
                            moduleTree,
                            buildSystemFilterSpec(
                                    3, true, resp, projectRelations, hasEditPermission));
        } else {
            totalCount =
                    countByFilterSpec(
                            rootModuleId,
                            moduleTree,
                            buildSystemFilterSpec(
                                    null, false, null, projectRelations, hasEditPermission));
        }

        log.info(
                "[ApprovalCount-Finish] 菜单审批角标统计完成: menuId={}, rootModuleId={}, 耗时={}ms, 统计结果=[待办:{}, 已办:{}, 全部:{}]",
                menuId,
                rootModuleId,
                System.currentTimeMillis() - countStart,
                todoCount,
                doneCount,
                totalCount);

        return List.of(
                MenuApprovalCountResp.of("待办", 1, todoCount),
                MenuApprovalCountResp.of("已办", 2, doneCount),
                MenuApprovalCountResp.of("全部", 3, totalCount));
    }

    @Override
    public MenuChangeCountResp getChangeCountByMenu(Long menuId) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        Long rootModuleId = getModuleIdByMenuOrThrow(menuId);
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        ModuleInfo rootModuleInfo = moduleTree != null ? moduleTree.getModule(rootModuleId) : null;
        int enableChange =
                (rootModuleInfo != null && rootModuleInfo.getEnableChange() != null)
                        ? rootModuleInfo.getEnableChange()
                        : 0;
        if (enableChange != 1) {
            return MenuChangeCountResp.of(0, 0L);
        }

        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;
        if (primaryTable == null || primaryTable.isBlank()) {
            return MenuChangeCountResp.of(enableChange, 0L);
        }

        String changeTable = primaryTable + "_change";
        List<FilterItem> filters =
                List.of(
                        FilterItem.eq(changeTable, "deleted", (byte) 0),
                        FilterItem.eq(changeTable, "read_status", 0));
        Long unreadCount =
                dataApi.countSingleTable(
                        SingleTableQueryReq.builder()
                                .tableName(changeTable)
                                .filters(filters)
                                .build());

        return MenuChangeCountResp.of(enableChange, unreadCount);
    }

    @Override
    public StatusSimpleResp toggleActiveStatusByMenu(Long menuId, MenuActiveToggleReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (req == null) {
            throw new IllegalArgumentException("请求体 req 不能为空");
        }
        List<String> businessNos = req.getBusinessNos();
        if (CollectionUtils.isEmpty(businessNos)) {
            throw new IllegalArgumentException("业务编号列表 businessNos 不能为空");
        }
        List<String> validBusinessNos =
                businessNos.stream()
                        .filter(no -> no != null && !no.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        if (validBusinessNos.isEmpty()) {
            throw new IllegalArgumentException("业务编号列表 businessNos 不能为空且至少包含一个有效单号");
        }

        boolean activate = req.isActivate();
        int originalStatus = activate ? 11 : 99;
        int targetStatus = activate ? 99 : 11;

        Long rootModuleId = getModuleIdByMenuOrThrow(menuId);
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        ModuleInfo rootModuleInfo = moduleTree != null ? moduleTree.getModule(rootModuleId) : null;
        Integer enableActive = rootModuleInfo != null ? rootModuleInfo.getEnableActive() : null;
        if (enableActive != null && enableActive != 1) {
            throw new BusinessException("当前菜单对应模块未开启启停功能 (enableActive != 1)");
        }

        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;
        if (primaryTable == null || primaryTable.isBlank()) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联有效的物理主表");
        }

        log.info(
                "[ActiveToggle-Start] 接收菜单批量单据启停请求: menuId={}, rootModuleId={}, primaryTable={}, activate={}, originalStatus={}, targetStatus={}, businessNosCount={}",
                menuId,
                rootModuleId,
                primaryTable,
                activate,
                originalStatus,
                targetStatus,
                validBusinessNos.size());

        DSLContext dsl = jooqDataContextFactory.getContext();
        Table<?> table = resolveTable(primaryTable);

        int updatedRows =
                dsl.update(table)
                        .set(DSL.field(DSL.name("business_status"), Integer.class), targetStatus)
                        .set(DSL.field(DSL.name("updated_by")), UserContext.getOperateUserId())
                        .set(DSL.field(DSL.name("updated_date")), LocalDateTime.now())
                        .where(
                                DSL.field(DSL.name("business_no"), String.class)
                                        .in(validBusinessNos))
                        .and(
                                DSL.field(DSL.name("business_status"), Integer.class)
                                        .eq(originalStatus))
                        .and(DSL.field(DSL.name("deleted"), Byte.class).eq((byte) 0))
                        .execute();

        StatusSimpleResp statusResp = resolveEnhancedStatus(rootModuleId, targetStatus);

        log.info(
                "[ActiveToggle-Finish] 菜单批量单据启停执行完成: menuId={}, primaryTable={}, 目标状态={}, 匹配并更新行数={}, 返回状态={}",
                menuId,
                primaryTable,
                targetStatus,
                updatedRows,
                statusResp);

        return statusResp;
    }

    // ==================== 2. 系统级过滤组合与审批流编排 ====================

    private PrimaryFilterResult resolveMenuSystemFilters(
            ModuleTreeDTO moduleTree, Long rootModuleId, Long roleId, Integer approvalType) {
        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        List<ApprovalBusinessNoResp> approvalResp =
                approvalEnabled ? fetchApprovalBusinessNos(rootModuleId) : null;
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        boolean hasEditPermission =
                hasModuleEditPermission(fetchModulePermission(rootModuleId, roleId));
        return resolveSystemFilters(
                moduleTree,
                buildSystemFilterSpec(
                        approvalType,
                        approvalEnabled,
                        approvalResp,
                        projectRelations,
                        hasEditPermission));
    }

    private FilterSpec buildSystemFilterSpec(
            Integer approvalType,
            boolean approvalChainEnabled,
            List<ApprovalBusinessNoResp> approvalResp,
            ProjectTableRelationsDTO projectRelations,
            boolean hasEditPermission) {
        if (!approvalChainEnabled) {
            return hasEditPermission
                    ? FilterSpec.of("NoApproval-EditAllowed").withAllActive()
                    : FilterSpec.of("NoApproval-ReadOnly")
                            .withDataPermission(projectRelations)
                            .withMyRecords();
        }
        if (approvalType == null) {
            return FilterSpec.of("Default").withDataPermission(projectRelations).withMyRecords();
        }

        List<String> businessNos = extractBusinessNosByType(approvalResp, approvalType);
        return switch (approvalType) {
            case 1 -> FilterSpec.of("Todo").withApproval(businessNos);
            case 2 -> FilterSpec.of("Done").withApproval(businessNos);
            default ->
                    FilterSpec.of("All")
                            .withApproval(businessNos)
                            .withDataPermission(projectRelations);
        };
    }

    private List<String> extractBusinessNosByType(
            List<ApprovalBusinessNoResp> approvalResp, Integer targetApprovalType) {
        if (CollectionUtils.isEmpty(approvalResp)) {
            return Collections.emptyList();
        }
        return approvalResp.stream()
                .filter(
                        item ->
                                item != null
                                        && item.getBusinessNumber() != null
                                        && !item.getBusinessNumber().isBlank())
                .filter(
                        item ->
                                targetApprovalType == null
                                        || targetApprovalType == 3
                                        || Objects.equals(
                                                targetApprovalType, item.getApprovalType()))
                .map(item -> item.getBusinessNumber().trim())
                .distinct()
                .toList();
    }

    private PrimaryFilterResult resolveSystemFilters(ModuleTreeDTO moduleTree, FilterSpec spec) {
        if (spec == null || !spec.hasEffectiveDimension()) {
            return PrimaryFilterResult.empty();
        }
        return commonFilterResolver.resolve(moduleTree, spec);
    }

    private long countByFilterSpec(Long rootModuleId, ModuleTreeDTO moduleTree, FilterSpec spec) {
        PrimaryFilterResult result = resolveSystemFilters(moduleTree, spec);
        if (result == null || result.isEmpty()) {
            return 0L;
        }
        Long count =
                dataApi.count(
                        QueryReq.builder()
                                .moduleId(rootModuleId)
                                .filters(result.filters())
                                .build());
        return count != null ? count : 0L;
    }

    private boolean isApprovalChainEnabled(Long rootModuleId) {
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

    private List<ApprovalBusinessNoResp> fetchApprovalBusinessNos(Long rootModuleId) {
        if (rootModuleId == null) {
            return null;
        }
        try {
            return approvalEngine.getApprovalBusinessNo(rootModuleId);
        } catch (Exception ex) {
            log.warn("获取模块审批单据列表失败: rootModuleId={}, err={}", rootModuleId, ex.getMessage());
            return null;
        }
    }

    // ==================== 4. 菜单与模块上下文服务编排 ====================

    private record TargetModuleContext(Long targetModuleId, Long targetMenuId) {}

    private ModuleTreeDTO resolveRootModuleTree(MenuListReq req) {
        if (req == null || CollectionUtils.isEmpty(req.getTables())) {
            throw new IllegalArgumentException("查询请求中的 tables 物理表规格不能为空");
        }

        Set<Long> moduleIds = new LinkedHashSet<>();
        for (MenuTableSpec table : req.getTables()) {
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

        List<ModuleTreeDTO> projectTrees =
                configApi.getProjectModuleTree(
                        AppContext.getProjectNo(), AppContext.getSubjectId());
        if (CollectionUtils.isEmpty(projectTrees)) {
            throw new BusinessException("当前项目 [" + AppContext.getProjectNo() + "] 下未配置任何模块树");
        }

        for (ModuleTreeDTO tree : projectTrees) {
            if (tree != null
                    && tree.getAllowedModuleIds() != null
                    && tree.getAllowedModuleIds().containsAll(moduleIds)) {
                return tree;
            }
        }

        throw new BusinessException(
                "所选菜单对应的模块 " + moduleIds + " 跨越了多个不同的业务根模块树或未在当前项目树中找到匹配的公共根模块");
    }

    private Map<Long, Long> buildModuleToMenuMap(MenuListReq req, Long rootModuleId) {
        Map<Long, Long> moduleToMenuMap = new HashMap<>();
        if (req != null && req.getTables() != null) {
            for (MenuTableSpec mSpec : req.getTables()) {
                if (mSpec != null && mSpec.getMenuId() != null) {
                    Long modId = openMenuApi.getModuleIdByMenuId(mSpec.getMenuId());
                    if (modId != null) {
                        moduleToMenuMap.put(modId, mSpec.getMenuId());
                    }
                }
            }
        }
        if (!moduleToMenuMap.containsKey(rootModuleId) && rootModuleId != null) {
            Long rootMenuId = queryMenuIdQuietly(rootModuleId);
            if (rootMenuId != null) {
                moduleToMenuMap.put(rootModuleId, rootMenuId);
            }
        }
        return moduleToMenuMap;
    }

    private TargetModuleContext resolveTargetModuleContext(
            MenuListReq req,
            Long rootModuleId,
            Long rootMenuId,
            ModuleTreeDTO moduleTree,
            Map<Long, Long> moduleToMenuMap) {
        Long targetModuleId = rootModuleId;
        Long targetMenuId = rootMenuId;
        int minDepth = Integer.MAX_VALUE;

        if (req != null && !CollectionUtils.isEmpty(req.getTables())) {
            for (MenuTableSpec mSpec : req.getTables()) {
                if (mSpec == null) {
                    continue;
                }
                Long mId =
                        mSpec.getMenuId() != null
                                ? openMenuApi.getModuleIdByMenuId(mSpec.getMenuId())
                                : null;
                if (mId == null) {
                    mId =
                            PhysicalQueryMapper.resolveModuleIdForTable(
                                    mSpec.getMenuId(),
                                    mSpec.getTableName(),
                                    moduleTree,
                                    openMenuApi::getModuleIdByMenuId);
                }
                if (mId != null && moduleTree != null) {
                    int depth = moduleTree.getModuleDepth(mId);
                    if (depth < minDepth) {
                        minDepth = depth;
                        targetModuleId = mId;
                        targetMenuId = mSpec.getMenuId();
                    }
                }
            }
        }

        if (targetMenuId == null) {
            targetMenuId = moduleToMenuMap.get(targetModuleId);
            if (targetMenuId == null && targetModuleId != null) {
                targetMenuId = queryMenuIdQuietly(targetModuleId);
            }
        }
        return new TargetModuleContext(targetModuleId, targetMenuId);
    }

    private List<Long> fetchLatestEnabledVersionIds(
            Long rootModuleId,
            String primaryTable,
            List<FilterItem> finalFilters,
            List<SortItem> finalSorts) {
        DataBlock lightBlock =
                dataApi.queryList(
                        QueryReq.builder()
                                .moduleId(rootModuleId)
                                .tables(
                                        List.of(
                                                TableSpec.of(
                                                        rootModuleId,
                                                        primaryTable,
                                                        "id",
                                                        "business_no")))
                                .filters(finalFilters)
                                .sorts(finalSorts)
                                .build());

        if (lightBlock == null || CollectionUtils.isEmpty(lightBlock.getRecords())) {
            return Collections.emptyList();
        }

        Set<String> businessNoSet = new LinkedHashSet<>();
        for (DataRow r : lightBlock.getRecords()) {
            if (r != null) {
                String bNo = r.getString(primaryTable, "business_no");
                if (bNo != null && !bNo.isBlank()) {
                    businessNoSet.add(bNo.trim());
                }
            }
        }
        return businessNoSet.isEmpty()
                ? Collections.emptyList()
                : dataApi.getLatestVersionIds(primaryTable, businessNoSet, ENABLED_BUSINESS_STATUS);
    }

    // ==================== 5. 权限元数据装配、结果转换与基础辅助 ====================

    private List<DataRecordItem> enhanceAndConvertRecords(
            List<DataRow> rawRecords,
            Long rootModuleId,
            String primaryTable,
            MenuHeaderColumnResp headerRoot) {
        if (CollectionUtils.isEmpty(rawRecords)) {
            return new ArrayList<>();
        }
        queryResultEnhanceService.enhanceDataRows(
                rawRecords, rootModuleId, primaryTable, headerRoot);
        List<DataRecordItem> items = new ArrayList<>(rawRecords.size());
        for (DataRow rawRow : rawRecords) {
            if (rawRow != null) {
                items.add(PhysicalQueryMapper.toDataRecordItem(rawRow));
            }
        }
        return items;
    }

    private List<ModuleMetaDTO> buildModuleMetaList(
            ModuleTreeDTO moduleTree, Long rootModuleId, Long menuId, Long roleId) {
        List<ModuleMetaDTO> metaList = new ArrayList<>();
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
                            resolveFieldPermission(modPerm, f.getId());
                    fieldMetaMap.put(
                            fullKey,
                            FieldMetaDTO.builder()
                                    .id(f.getId())
                                    .displayName(f.getDisplayName())
                                    .apply(fp == null || fp.getApply())
                                    .view(fp == null || fp.getView())
                                    .edit(fp == null || fp.getEdit())
                                    .build());
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

    private ModulePermissionDTO.FieldPermissionItem resolveFieldPermission(
            ModulePermissionDTO modPerm, Long fieldId) {
        if (modPerm == null) {
            return null;
        }
        if (modPerm.getModuleFieldPermissions() != null) {
            return modPerm.getModuleFieldPermissions().get(fieldId);
        }
        if (modPerm.getModuleTreeFieldPermissions() != null) {
            return modPerm.getModuleTreeFieldPermissions().get(fieldId);
        }
        return null;
    }

    private ModulePermissionDTO fetchModulePermission(Long rootModuleId, Long roleId) {
        if (rootModuleId == null || roleId == null) {
            return null;
        }
        try {
            Map<Long, ModulePermissionDTO> permMap =
                    configApi.getModulePermissions(roleId, Collections.singletonList(rootModuleId));
            return permMap != null ? permMap.get(rootModuleId) : null;
        } catch (Exception ex) {
            log.warn(
                    "获取模块权限汇总失败: rootModuleId={}, roleId={}, err={}",
                    rootModuleId,
                    roleId,
                    ex.getMessage());
            return null;
        }
    }

    private boolean hasModuleEditPermission(ModulePermissionDTO perm) {
        return perm != null && Boolean.TRUE.equals(perm.getEdit());
    }

    private MenuDataResp assembleMenuDataResp(
            MenuHeaderColumnResp headerRoot,
            ModulePermissionDTO rootModulePerm,
            ModuleDataNode dataNode,
            List<ActionButtonDTO> globalActions,
            List<ActionRowItem> approvalRows,
            List<ActionRowItem> customActions,
            List<ActionRowItem> interactionActions) {
        ModuleSimplePermissionDTO permission =
                rootModulePerm == null
                        ? null
                        : ModuleSimplePermissionDTO.builder()
                                .apply(Boolean.TRUE.equals(rootModulePerm.getApply()) ? 1 : 0)
                                .view(Boolean.TRUE.equals(rootModulePerm.getView()) ? 1 : 0)
                                .edit(Boolean.TRUE.equals(rootModulePerm.getEdit()) ? 1 : 0)
                                .build();

        return MenuDataResp.builder()
                .header(headerRoot)
                .modulePermission(permission)
                .globalActions(globalActions)
                .approvalActions(approvalRows)
                .customActions(customActions)
                .interactionActions(interactionActions)
                .data(dataNode)
                .build();
    }

    private MenuHeaderColumnResp queryHeaderRootQuietly(Long menuId, Long roleId) {
        if (menuId == null) {
            return null;
        }
        try {
            return getHeaderDisplayOrThrow(getEffectiveUserId(), menuId, roleId);
        } catch (Exception ex) {
            log.debug("获取菜单列头配置失败: menuId={}, roleId={}, err={}", menuId, roleId, ex.getMessage());
            return null;
        }
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

    private Long getModuleIdByMenuOrThrow(Long menuId) {
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            MenuHeaderColumnResp headerRoot =
                    queryHeaderRootQuietly(menuId, UserContext.getRoleId());
            if (headerRoot != null) {
                rootModuleId = headerRoot.getModuleId();
            }
        }
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }
        return rootModuleId;
    }

    private Long queryMenuIdQuietly(Long moduleId) {
        try {
            return openMenuApi.getMenuIdByModuleId(moduleId);
        } catch (Exception ex) {
            log.debug("通过 openMenuApi 解析模块 menuId 失败: moduleId={}", moduleId);
            return null;
        }
    }

    private ModuleDataNode buildEmptyModuleDataNode(Long moduleId, Long menuId) {
        return ModuleDataNode.builder()
                .moduleId(moduleId)
                .menuId(menuId)
                .total(0L)
                .record(Collections.emptyList())
                .build();
    }

    private Long getEffectiveUserId() {
        return UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
    }

    private Table<?> resolveTable(String tableName) {
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            return DSL.table(DSL.name(parts[0], parts[1]));
        }
        return DSL.table(DSL.name(tableName));
    }

    private StatusSimpleResp resolveEnhancedStatus(Long rootModuleId, Integer targetStatus) {
        if (sysStatusApi != null && rootModuleId != null && targetStatus != null) {
            try {
                ModuleApprovalChainConfigResp.StatusNode node =
                        sysStatusApi.getStatusByValue(targetStatus, rootModuleId);
                if (node != null) {
                    return StatusSimpleResp.builder()
                            .title(node.getTitle())
                            .statusValue(node.getStatusValue())
                            .statusBackground(node.getStatusBackground())
                            .statusFontColor(node.getStatusFontColor())
                            .build();
                }
            } catch (Exception ex) {
                log.warn(
                        "获取模块增强状态信息失败: moduleId={}, statusValue={}, err={}",
                        rootModuleId,
                        targetStatus,
                        ex.getMessage());
            }
        }
        String fallbackTitle = (targetStatus != null && targetStatus == 99) ? "启用" : "停用";
        return StatusSimpleResp.builder().statusValue(targetStatus).title(fallbackTitle).build();
    }
}
