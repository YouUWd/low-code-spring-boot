package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.MenuListReq;
import com.jdec.platform.common.api.dto.request.MenuOptionReq;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.request.MenuTableSpec;
import com.jdec.platform.common.api.dto.response.ApprovalRowItem;
import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.FieldMetaDTO;
import com.jdec.platform.common.api.dto.response.MenuApprovalCountResp;
import com.jdec.platform.common.api.dto.response.MenuDataResp;
import com.jdec.platform.common.api.dto.response.MenuDetailResp;
import com.jdec.platform.common.api.dto.response.ModuleDataNode;
import com.jdec.platform.common.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.common.biz.custom.CustomActionContext;
import com.jdec.platform.common.biz.custom.RowCustomActionService;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.common.biz.service.QueryResultEnhanceService;
import com.jdec.platform.common.biz.service.impl.CommonFilterResolver.FilterSpec;
import com.jdec.platform.common.biz.service.impl.CommonFilterResolver.PrimaryFilterResult;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysFileApi;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
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
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * 公共业务聚合服务实现 (基于菜单的数据隔离、动态列表与详情聚合)
 *
 * <p>Service 层仅负责跨服务业务编排（表头配置、模块树元数据、系统级过滤组合决策、数据引擎查询与字典增强），底层逻辑均已下沉：
 *
 * <ul>
 *   <li>{@link CommonFilterResolver}：系统级过滤条件（审批引擎 + 数据权限 + 草稿）灵活组合与主表物理 ID 求解。
 *   <li>{@link ApprovalConditionResolver}：审批单据提取与行级操作按钮（办理 / 查阅）组装。
 *   <li>{@link PhysicalQueryMapper}：1:N 模块分支冲突校验、表头字段收集、物理表/过滤/排序消歧映射与 {@link DataRow} 同构树转换。
 *   <li>{@link ModuleTreeDTO}：模块树层级深度计算、1:N 顶层分支拓扑推导与物理表归属检索。
 *   <li>{@link QueryResultEnhanceService}：查询结果与候选项数据增强（用户信息、业务状态、文件/图片等）。
 * </ul>
 */
@Slf4j
@Service
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
    private final RowCustomActionService rowCustomActionService;

    @Autowired
    public CommonAggregateServiceImpl(
            UserMenuHeaderApi userMenuHeaderApi,
            OpenMenuApi openMenuApi,
            ConfigApi configApi,
            DataApi dataApi,
            CommonFilterResolver commonFilterResolver,
            IApprovalEngine approvalEngine,
            SysApprovalChainConfigApi sysApprovalChainConfigApi,
            QueryResultEnhanceService queryResultEnhanceService,
            RowCustomActionService rowCustomActionService) {
        this.userMenuHeaderApi = userMenuHeaderApi;
        this.openMenuApi = openMenuApi;
        this.configApi = configApi;
        this.dataApi = dataApi;
        this.commonFilterResolver = commonFilterResolver;
        this.approvalEngine = approvalEngine;
        this.sysApprovalChainConfigApi = sysApprovalChainConfigApi;
        this.queryResultEnhanceService = queryResultEnhanceService;
        this.rowCustomActionService = rowCustomActionService;
    }

    public CommonAggregateServiceImpl(
            UserMenuHeaderApi userMenuHeaderApi,
            OpenMenuApi openMenuApi,
            ConfigApi configApi,
            DataApi dataApi,
            CommonFilterResolver commonFilterResolver,
            IApprovalEngine approvalEngine,
            SysApprovalChainConfigApi sysApprovalChainConfigApi,
            QueryResultEnhanceService queryResultEnhanceService) {
        this(
                userMenuHeaderApi,
                openMenuApi,
                configApi,
                dataApi,
                commonFilterResolver,
                approvalEngine,
                sysApprovalChainConfigApi,
                queryResultEnhanceService,
                new RowCustomActionService(Collections.emptyList()));
    }

    public CommonAggregateServiceImpl(
            UserMenuHeaderApi userMenuHeaderApi,
            OpenMenuApi openMenuApi,
            ConfigApi configApi,
            DataApi dataApi,
            CommonFilterResolver commonFilterResolver,
            SysUserApi sysUserApi,
            SysStatusApi sysStatusApi,
            IApprovalEngine approvalEngine,
            SysApprovalChainConfigApi sysApprovalChainConfigApi,
            SysFileApi sysFileApi) {
        this(
                userMenuHeaderApi,
                openMenuApi,
                configApi,
                dataApi,
                commonFilterResolver,
                approvalEngine,
                sysApprovalChainConfigApi,
                new QueryResultEnhanceServiceImpl(sysUserApi, sysStatusApi));
    }

    public CommonAggregateServiceImpl(
            UserMenuHeaderApi userMenuHeaderApi,
            OpenMenuApi openMenuApi,
            ConfigApi configApi,
            DataApi dataApi,
            CommonFilterResolver commonFilterResolver,
            SysUserApi sysUserApi,
            SysStatusApi sysStatusApi,
            IApprovalEngine approvalEngine,
            SysApprovalChainConfigApi sysApprovalChainConfigApi) {
        this(
                userMenuHeaderApi,
                openMenuApi,
                configApi,
                dataApi,
                commonFilterResolver,
                sysUserApi,
                sysStatusApi,
                approvalEngine,
                sysApprovalChainConfigApi,
                null);
    }

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

        // 1. 获取用户在该菜单下的个性化表头展示配置及模块树拓扑
        MenuHeaderColumnResp headerRoot = getHeaderDisplayOrThrow(userId, menuId, roleId);
        Long rootModuleId = headerRoot.getModuleId();
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;

        // 2. 委托 PhysicalQueryMapper 校验 1:N 分支、修饰表头并收集勾选字段，转换为物理查询参数
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

        // 3. 灵活组合系统级过滤条件 (审批引擎 + 数据权限 + 草稿)
        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        ApprovalIdButtonListResp approvalResp =
                approvalEnabled ? fetchApprovalButtonList(rootModuleId) : null;
        ModulePermissionDTO rootModulePerm = fetchModulePermission(rootModuleId, roleId);
        boolean hasEditPermission = hasModuleEditPermission(rootModulePerm);

        log.info(
                "[PageQuery-Context] 菜单关联根模块及主表: menuId={}, rootModuleId={}, primaryTable={}, approvalEnabled={}, hasEditPermission={}",
                menuId,
                rootModuleId,
                primaryTable,
                approvalEnabled,
                hasEditPermission);

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
        String moduleCode = rootModuleInfo != null ? rootModuleInfo.getModuleCode() : null;
        Integer enableActive =
                (rootModuleInfo != null && rootModuleInfo.getEnableActive() != null)
                        ? rootModuleInfo.getEnableActive()
                        : 0;
        Long changeModuleId = rootModuleInfo != null ? rootModuleInfo.getChangeModuleId() : null;

        CustomActionContext customActionCtx =
                new CustomActionContext(
                        AppContext.getProjectNo(),
                        rootModuleId,
                        moduleCode,
                        primaryTable,
                        enableActive,
                        changeModuleId,
                        userId,
                        roleId,
                        hasEditPermission);

        if (filterResult.isEmpty()) {
            log.info(
                    "[PageQuery-ShortCircuit] 主表权限/审批过滤结果为空，短路直接返回空页面: menuId={}, rootModuleId={}, primaryTable={}",
                    menuId,
                    rootModuleId,
                    primaryTable);
            ModuleDataNode emptyNode = buildEmptyModuleDataNode(rootModuleId, menuId);
            List<ApprovalRowItem> emptyApprovalRows =
                    approvalEnabled ? Collections.emptyList() : null;
            List<ApprovalRowItem> emptyCustomActions =
                    (rowCustomActionService != null
                                    && rowCustomActionService.hasActiveProviders(customActionCtx))
                            ? Collections.emptyList()
                            : null;
            return assembleMenuDataResp(
                    headerRoot, rootModulePerm, emptyNode, emptyApprovalRows, emptyCustomActions);
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

        Long total =
                (rawDataPage != null && rawDataPage.getTotal() != null)
                        ? rawDataPage.getTotal()
                        : 0L;
        List<DataRow> rawRecords =
                (rawDataPage != null && rawDataPage.getRecords() != null)
                        ? rawDataPage.getRecords()
                        : Collections.emptyList();

        log.info(
                "[PageQuery-Execute] 底层低代码数据引擎分页查询完成: menuId={}, rootModuleId={}, 耗时={}ms, total={}, 当前页行数={}, pageNo={}, pageSize={}",
                menuId,
                rootModuleId,
                queryPageCost,
                total,
                rawRecords.size(),
                pageNo,
                pageSize);

        // 5. 增强主表字典字段并委托 PhysicalQueryMapper 与 ApprovalConditionResolver 组装响应
        List<DataRecordItem> recordItems = new ArrayList<>(rawRecords.size());
        if (!rawRecords.isEmpty()) {
            queryResultEnhanceService.enhanceDataRows(
                    rawRecords, rootModuleId, primaryTable, headerRoot);
            for (DataRow rawRow : rawRecords) {
                if (rawRow != null) {
                    recordItems.add(PhysicalQueryMapper.toDataRecordItem(rawRow));
                }
            }
        }

        List<ApprovalRowItem> approvalRows =
                approvalEnabled
                        ? ApprovalConditionResolver.buildApprovalRows(
                                rawRecords, primaryTable, approvalType, approvalResp, userId)
                        : null;

        List<ApprovalRowItem> customActions =
                rowCustomActionService != null
                        ? rowCustomActionService.buildCustomActions(rawRecords, customActionCtx)
                        : null;

        ModuleDataNode rootNode =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(menuId)
                        .total(total)
                        .record(recordItems)
                        .build();

        log.info(
                "[PageQuery-Finish] 分页查询组装完成: menuId={}, rootModuleId={}, 总耗时={}ms, total={}, 返回记录数={}, 审批行数={}, 定制操作行数={}",
                menuId,
                rootModuleId,
                System.currentTimeMillis() - queryStart,
                total,
                recordItems.size(),
                approvalRows != null ? approvalRows.size() : "null(未启用审批)",
                customActions != null ? customActions.size() : "null(无定制操作)");

        return assembleMenuDataResp(
                headerRoot, rootModulePerm, rootNode, approvalRows, customActions);
    }

    @Override
    public ModuleDataNode queryList(MenuListReq req) {
        // 1. 定位目标公共根模块树与最顶层目标模块上下文
        ModuleTreeDTO moduleTree = resolveRootModuleTree(req);
        Long rootModuleId = moduleTree.getRootModuleId();
        String primaryTable = moduleTree.getPrimaryTable();

        Map<Long, Long> moduleToMenuMap = buildModuleToMenuMap(req, rootModuleId);
        Long rootMenuId = moduleToMenuMap.get(rootModuleId);
        TargetModuleContext targetCtx =
                resolveTargetModuleContext(
                        req, rootModuleId, rootMenuId, moduleTree, moduleToMenuMap);

        // 2. 委托 PhysicalQueryMapper 将平铺物理表/过滤/排序规格转换为底层引擎参数
        List<TableSpec> tableSpecs =
                PhysicalQueryMapper.toTableSpecs(
                        req != null ? req.getTables() : null,
                        moduleTree,
                        openMenuApi::getModuleIdByMenuId);
        Map<String, Set<String>> requestedTableColumnsMap =
                PhysicalQueryMapper.buildRequestedTableColumnsMap(
                        req != null ? req.getTables() : null,
                        moduleTree,
                        openMenuApi::getModuleIdByMenuId);
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

        // 3. 灵活组合系统级过滤条件 (默认查询全部集合: approvalType = 3)
        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        ApprovalIdButtonListResp approvalResp =
                approvalEnabled ? fetchApprovalButtonList(rootModuleId) : null;
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        Long roleId = UserContext.getRoleId();
        boolean hasEditPermission =
                hasModuleEditPermission(fetchModulePermission(rootModuleId, roleId));
        PrimaryFilterResult filterResult =
                resolveSystemFilters(
                        moduleTree,
                        buildSystemFilterSpec(
                                3,
                                approvalEnabled,
                                approvalResp,
                                projectRelations,
                                hasEditPermission));
        if (filterResult.isEmpty()) {
            return buildEmptyModuleDataNode(targetCtx.targetModuleId(), targetCtx.targetMenuId());
        }
        if (!filterResult.filters().isEmpty()) {
            finalFilters.addAll(filterResult.filters());
        }

        // 4. 求解匹配的每个 business_no 最新启用 (business_status = 99) 的最大版本主键 ID 列表
        List<Long> latestEnabledIds =
                fetchLatestEnabledVersionIds(rootModuleId, primaryTable, finalFilters, finalSorts);
        if (CollectionUtils.isEmpty(latestEnabledIds)) {
            return buildEmptyModuleDataNode(targetCtx.targetModuleId(), targetCtx.targetMenuId());
        }

        // 5. 基于唯一主键 ID 列表进行全量关联投影查询
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

        // 6. 委托 PhysicalQueryMapper 转换为自相似同构树记录列表，并按目标模块层级裁剪提取
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

        // 1. 获取表头展示配置与模块树，委托 PhysicalQueryMapper 收集字段并生成表投影规格
        MenuHeaderColumnResp headerRoot = getHeaderDisplayOrThrow(userId, menuId, roleId);
        Long rootModuleId = headerRoot.getModuleId();
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);

        List<Long> checkedFieldIds = PhysicalQueryMapper.collectCheckedFieldIds(headerRoot);
        List<TableSpec> tableSpecs = PhysicalQueryMapper.toTableSpecs(moduleTree, checkedFieldIds);

        // 2. 调用底层低代码引擎获取单据详情
        DataBlock dataBlock =
                dataApi.getDetail(
                        DetailQueryReq.builder()
                                .moduleId(rootModuleId)
                                .id(id)
                                .tables(tableSpecs)
                                .build());

        // 3. 增强字段并转换为 ModuleDataNode 树
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

        // 4. 构建模块元数据配置列表并返回响应
        List<ModuleMetaDTO> metaList =
                buildModuleMetaList(moduleTree, rootModuleId, menuId, roleId);
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

        // 1. 解析菜单关联的根模块 ID 与目标物理字段
        Long rootModuleId = getModuleIdByMenuOrThrow(menuId);
        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        PhysicalQueryMapper.PhysicalFieldRef targetField =
                PhysicalQueryMapper.resolve(moduleTree, req.getFieldId());

        // 2. 组装前端传入的过滤条件与系统级过滤条件 (审批引擎 + 数据权限 + 草稿)
        List<FilterItem> finalFilters =
                PhysicalQueryMapper.toTableFilters(moduleTree, req.getFilters());

        Integer approvalType = req.getApprovalType();
        boolean approvalEnabled = isApprovalChainEnabled(rootModuleId);
        ApprovalIdButtonListResp approvalResp =
                approvalEnabled ? fetchApprovalButtonList(rootModuleId) : null;
        ProjectTableRelationsDTO projectRelations =
                configApi.getProjectTableRelations(AppContext.getProjectNo());
        Long roleId = UserContext.getRoleId();
        boolean hasEditPermission =
                hasModuleEditPermission(fetchModulePermission(rootModuleId, roleId));

        PrimaryFilterResult filterResult =
                resolveSystemFilters(
                        moduleTree,
                        buildSystemFilterSpec(
                                approvalType,
                                approvalEnabled,
                                approvalResp,
                                projectRelations,
                                hasEditPermission));
        if (filterResult.isEmpty()) {
            return Collections.emptyList();
        }
        if (!filterResult.filters().isEmpty()) {
            finalFilters.addAll(filterResult.filters());
        }

        // 3. 调用底层低代码数据引擎获取候选项并增强展示
        OptionReq optionReq =
                OptionReq.builder()
                        .moduleId(rootModuleId)
                        .tableName(targetField.tableName())
                        .columnName(targetField.columnName())
                        .filters(finalFilters)
                        .direction(req.getDirection())
                        .build();

        List<DataOptionResp> rawOptions = dataApi.getOptions(optionReq);
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
        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;

        long countStart = System.currentTimeMillis();
        Long currentUserId = getEffectiveUserId();
        Long roleId = UserContext.getRoleId();
        boolean hasEditPermission =
                hasModuleEditPermission(fetchModulePermission(rootModuleId, roleId));
        log.info(
                "[ApprovalCount-Start] 开始统计菜单审批角标: menuId={}, rootModuleId={}, primaryTable={}, currentUserId={}, roleId={}, hasEditPermission={}",
                menuId,
                rootModuleId,
                primaryTable,
                currentUserId,
                roleId,
                hasEditPermission);

        long todoCount = 0L;
        long doneCount = 0L;
        long totalCount;

        if (isApprovalChainEnabled(rootModuleId)) {
            ApprovalIdButtonListResp resp = fetchApprovalButtonList(rootModuleId);
            // 待办 (type = 1): 仅【审批引擎(待办)】
            todoCount =
                    countByFilterSpec(
                            rootModuleId,
                            moduleTree,
                            buildSystemFilterSpec(
                                    1, true, resp, projectRelations, hasEditPermission));
            // 已办 (type = 2): 仅【审批引擎(已办)】
            doneCount =
                    countByFilterSpec(
                            rootModuleId,
                            moduleTree,
                            buildSystemFilterSpec(
                                    2, true, resp, projectRelations, hasEditPermission));
            // 全部 (type = 3): 【审批引擎(全部)】 + 【数据权限】
            totalCount =
                    countByFilterSpec(
                            rootModuleId,
                            moduleTree,
                            buildSystemFilterSpec(
                                    3, true, resp, projectRelations, hasEditPermission));
        } else {
            // 未启用审批流:
            // 若用户角色拥有模块编辑权限，移除限制性过滤条件，统计所有 active=1 的数据；若无编辑权限，按【数据权限】+【个人全部单据】统计
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

    // ==================== 2. 系统级过滤组合与审批流编排 ====================

    /**
     * 按业务场景灵活组合系统级过滤条件（审批引擎 + 数据权限 + 草稿）
     *
     * <ul>
     *   <li>未启用审批且有编辑权限：【全部有效数据】(active = 1，移除限制性 filter)
     *   <li>未启用审批且无编辑权限：【数据权限】+【个人全部单据】
     *   <li>1-待办：仅【审批引擎(待办)】
     *   <li>2-已办：仅【审批引擎(已办)】
     *   <li>3-全部：【审批引擎(全部)】+【数据权限】
     * </ul>
     */
    private FilterSpec buildSystemFilterSpec(
            Integer approvalType,
            boolean approvalChainEnabled,
            ApprovalIdButtonListResp approvalResp,
            ProjectTableRelationsDTO projectRelations,
            boolean hasEditPermission) {
        if (!approvalChainEnabled) {
            if (hasEditPermission) {
                return FilterSpec.of("NoApproval-EditAllowed").withAllActive();
            }
            return FilterSpec.of("NoApproval-ReadOnly")
                    .withDataPermission(projectRelations)
                    .withMyRecords();
        }
        if (approvalType == null) {
            return FilterSpec.of("Default").withDataPermission(projectRelations).withMyRecords();
        }
        return switch (approvalType) {
            case 1 ->
                    FilterSpec.of("Todo")
                            .withApprovalButtons(
                                    approvalResp != null ? approvalResp.getToDoList() : null);
            case 2 ->
                    FilterSpec.of("Done")
                            .withApprovalButtons(
                                    approvalResp != null ? approvalResp.getDoneList() : null);
            default ->
                    FilterSpec.of("All")
                            .withApprovalButtons(
                                    approvalResp != null ? approvalResp.getAllList() : null)
                            .withDataPermission(projectRelations);
        };
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

    private ApprovalIdButtonListResp fetchApprovalButtonList(Long rootModuleId) {
        if (rootModuleId == null) {
            return null;
        }
        try {
            return approvalEngine.getApprovalListButton(rootModuleId);
        } catch (Exception ex) {
            log.warn("获取模块审批单据列表失败: rootModuleId={}, err={}", rootModuleId, ex.getMessage());
            return null;
        }
    }

    // ==================== 3. 菜单与模块上下文服务编排 ====================

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

        if (targetModuleId == null) {
            targetModuleId = rootModuleId;
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
        TableSpec lightSpec = TableSpec.of(rootModuleId, primaryTable, "id", "business_no");
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
        return dataApi.getLatestVersionIds(primaryTable, businessNoSet, ENABLED_BUSINESS_STATUS);
    }

    // ==================== 4. 权限元数据装配与字典增强 ====================

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
                            (modPerm != null && modPerm.getModuleFieldPermissions() != null)
                                    ? modPerm.getModuleFieldPermissions().get(f.getId())
                                    : (modPerm != null
                                                    && modPerm.getModuleTreeFieldPermissions()
                                                            != null)
                                            ? modPerm.getModuleTreeFieldPermissions().get(f.getId())
                                            : null;

                    fieldMetaMap.put(
                            fullKey,
                            FieldMetaDTO.builder()
                                    .id(f.getId())
                                    .displayName(f.getDisplayName())
                                    .apply(fp != null ? fp.getApply() : true)
                                    .view(fp != null ? fp.getView() : true)
                                    .edit(fp != null ? fp.getEdit() : true)
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
            List<ApprovalRowItem> approvalRows,
            List<ApprovalRowItem> customActions) {
        ModuleSimplePermissionDTO permission =
                ModuleSimplePermissionDTO.builder()
                        .apply(
                                rootModulePerm != null
                                                && Boolean.TRUE.equals(rootModulePerm.getApply())
                                        ? 1
                                        : 0)
                        .view(
                                rootModulePerm != null
                                                && Boolean.TRUE.equals(rootModulePerm.getView())
                                        ? 1
                                        : 0)
                        .edit(
                                rootModulePerm != null
                                                && Boolean.TRUE.equals(rootModulePerm.getEdit())
                                        ? 1
                                        : 0)
                        .build();
        if (rootModulePerm == null) {
            permission = ModuleSimplePermissionDTO.builder().apply(1).view(1).edit(1).build();
        }

        return MenuDataResp.builder()
                .header(headerRoot)
                .permission(permission)
                .approval(approvalRows)
                .customActions(customActions)
                .data(dataNode)
                .build();
    }

    private MenuHeaderColumnResp queryHeaderRootQuietly(Long menuId, Long roleId) {
        if (menuId == null) {
            return null;
        }
        try {
            Long userId = getEffectiveUserId();
            return getHeaderDisplayOrThrow(userId, menuId, roleId);
        } catch (Exception ex) {
            log.debug("获取菜单列头配置失败: menuId={}, roleId={}, err={}", menuId, roleId, ex.getMessage());
            return null;
        }
    }

    // ==================== 5. 公共基础辅助方法 ====================

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
}
