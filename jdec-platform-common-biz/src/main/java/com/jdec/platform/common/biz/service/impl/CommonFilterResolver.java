package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.dto.ApprpvalIdButton;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenRolePermissionApi;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 统一通用过滤条件解析组件 (CommonFilterResolver)
 *
 * <p>业务过滤逻辑：
 *
 * <ul>
 *   <li>1. 区分 rootModuleId 是否带有审批链。
 *   <li>2. 有审批链：
 *       <ul>
 *         <li>已办 (type = 2)：仅过滤审批已办 business_no 集合，AND active = 1（不考虑 created_by 与数据权限）。
 *         <li>待办 (type = 1)：[审批待办 business_no] OR [created_by = :me AND business_status = 1]，AND
 *             active = 1。
 *         <li>全部 (type = 3)：[审批全部 business_no] OR [created_by = :me business_no (不限状态)] OR [数据权限
 *             business_no]，AND active = 1。
 *       </ul>
 *   <li>3. 没有审批链（或未传审批类型）：
 *       <ul>
 *         <li>[created_by = :me business_no (不限状态)] OR [数据权限 business_no]，AND active = 1。
 *       </ul>
 *   <li>4. 所有单据集合均采用 DISTINCT 抽取，最终组合时统一按主表 business_no 进行过滤，最后加上 AND active = 1。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonFilterResolver {

    public static final String SUBJECT_ID_COLUMN = "subject_id";
    public static final String BUSINESS_NO_COLUMN = "business_no";
    public static final String ACTIVE_COLUMN = "active";
    public static final String CREATED_BY_COLUMN = "created_by";
    public static final String BUSINESS_STATUS_COLUMN = "business_status";

    private final IApprovalEngine approvalEngine;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final ConfigApi configApi;
    private final OpenRolePermissionApi openRolePermissionApi;
    private final com.jdec.platform.data.api.DataApi dataApi;

    /** 检查模块是否启用了审批流 */
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

    /** 统一为列表与字段下拉解析审批过滤、数据权限与按钮 (Search / Option) */
    public ApprovalFilterItem resolveApprovalFilters(
            Long rootModuleId, Integer approvalType, ModuleTreeDTO moduleTree) {
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        boolean approvalChainEnabled = isApprovalChainEnabled(rootModuleId);

        // 未开启审批链或未指定审批类型：执行无审批链逻辑
        if (!approvalChainEnabled || approvalType == null) {
            return buildNoApprovalFilterItem(rootFieldIds);
        }

        // 开启审批链：拉取审批流按钮与单据
        Map<String, List<Map<String, Object>>> btnMap =
                queryApprovalButtons(rootModuleId, approvalType);

        return buildApprovalFilterItem(approvalType, rootFieldIds, btnMap);
    }

    /** 一次性拉取全部审批流过滤，供计数服务 (CommonAggregateServiceImpl) 分别统计待办/已办/全部 */
    public AllApprovalCountFilters resolveAllApprovalCountsFilters(
            Long rootModuleId, ModuleTreeDTO moduleTree) {
        if (rootModuleId == null || !isApprovalChainEnabled(rootModuleId)) {
            return new AllApprovalCountFilters(false, null, null, null);
        }

        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        try {
            com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp resp =
                    approvalEngine.getApprovalListButton(rootModuleId);
            Map<String, List<Map<String, Object>>> todoBtns =
                    resp != null
                            ? parseButtonsByRecord(resp.getToDoList())
                            : Collections.emptyMap();
            Map<String, List<Map<String, Object>>> doneBtns =
                    resp != null
                            ? parseButtonsByRecord(resp.getDoneList())
                            : Collections.emptyMap();
            Map<String, List<Map<String, Object>>> allBtns =
                    resp != null ? parseButtonsByRecord(resp.getAllList()) : Collections.emptyMap();

            // 预先抽取由我创建的 business_no 集合与数据权限 business_no 集合，供待办/全部复用
            // 待办 (type = 1) 仅统计草稿 (business_status = 1)
            List<String> myDraftBusinessNos = queryMyCreatedBusinessNos(rootFieldIds, 1);
            // 全部 (type = 3) 统计全部由我创建 (不限状态)
            List<String> myAllBusinessNos = queryMyCreatedBusinessNos(rootFieldIds, null);
            List<FilterItem> dataPermFilters = buildDataPermissionFilters(rootFieldIds.tree());
            List<String> dataPermBusinessNos =
                    queryDataPermissionRecordIds(rootFieldIds.tree(), dataPermFilters);

            return new AllApprovalCountFilters(
                    true,
                    buildApprovalFilterItem(
                            1, rootFieldIds, todoBtns, myDraftBusinessNos, dataPermBusinessNos),
                    buildApprovalFilterItem(
                            2,
                            rootFieldIds,
                            doneBtns,
                            Collections.emptyList(),
                            dataPermBusinessNos),
                    buildApprovalFilterItem(
                            3, rootFieldIds, allBtns, myAllBusinessNos, dataPermBusinessNos));
        } catch (Exception ex) {
            log.warn("批量获取审批链按钮失败: moduleId={}, err={}", rootModuleId, ex.getMessage());
            return new AllApprovalCountFilters(false, null, null, null);
        }
    }

    // ==================== 1. 独立解析器：我创建的单据 (created_by = :me) ====================

    /** 调用 DataApi 抽取当前登录人创建的主表 business_no 列表 (不限状态，distinct, active = 1) */
    public List<String> queryMyCreatedBusinessNos(RootModuleFieldIds rootFieldIds) {
        return queryMyCreatedBusinessNos(rootFieldIds, null);
    }

    /** 调用 DataApi 抽取当前登录人创建的主表 business_no 列表 (指定业务状态 business_status，distinct, active = 1) */
    public List<String> queryMyCreatedBusinessNos(
            RootModuleFieldIds rootFieldIds, Integer businessStatus) {
        if (rootFieldIds == null
                || rootFieldIds.tree() == null
                || rootFieldIds.tree().getRootModule() == null) {
            return Collections.emptyList();
        }
        Long currentUserId =
                UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
        if (currentUserId == null) {
            return Collections.emptyList();
        }

        String primaryTable = rootFieldIds.tree().getRootModule().getPrimaryTable();
        Long rootModuleId = rootFieldIds.tree().getRootModule().getModuleId();
        if (primaryTable == null || primaryTable.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<FilterItem> filters = new ArrayList<>();
            // created_by = :me
            if (rootFieldIds.createdByField() != null) {
                filters.add(
                        PhysicalQueryMapper.filter(
                                rootFieldIds.tree(),
                                rootFieldIds.createdByField().fieldId(),
                                "EQ",
                                currentUserId));
            } else {
                FilterItem f = FilterItem.eq(primaryTable, CREATED_BY_COLUMN, currentUserId);
                f.setModuleId(rootModuleId);
                filters.add(f);
            }

            // business_status = :businessStatus
            if (businessStatus != null) {
                if (rootFieldIds.businessStatusField() != null) {
                    filters.add(
                            PhysicalQueryMapper.filter(
                                    rootFieldIds.tree(),
                                    rootFieldIds.businessStatusField().fieldId(),
                                    "EQ",
                                    businessStatus));
                } else {
                    FilterItem f =
                            FilterItem.eq(primaryTable, BUSINESS_STATUS_COLUMN, businessStatus);
                    f.setModuleId(rootModuleId);
                    filters.add(f);
                }
            }

            // active = 1
            filters.add(buildActiveFilter(rootFieldIds));

            com.jdec.platform.data.api.dto.request.SingleTableQueryReq singleReq =
                    com.jdec.platform.data.api.dto.request.SingleTableQueryReq.of(
                            primaryTable, BUSINESS_NO_COLUMN);
            singleReq.setFilters(filters);
            singleReq.setDistinct(true);

            List<String> nos = dataApi.querySingleTableColumn(singleReq, String.class);
            return (nos != null && !nos.isEmpty())
                    ? new ArrayList<>(new LinkedHashSet<>(nos))
                    : Collections.emptyList();
        } catch (Exception ex) {
            log.warn(
                    "通过 DataApi 抽取 created_by business_no 失败: table={}, status={}, err={}",
                    primaryTable,
                    businessStatus,
                    ex.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 2. 独立解析器：审批流单据过滤 (Approval Record Filters) ====================

    /** 向审批引擎查询指定审批类型的记录与按钮列表（以 business_no 为主键） */
    public Map<String, List<Map<String, Object>>> queryApprovalButtons(
            Long rootModuleId, Integer approvalType) {
        if (rootModuleId == null || approvalType == null) {
            return Collections.emptyMap();
        }
        try {
            com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp resp =
                    approvalEngine.getApprovalListButton(rootModuleId);
            if (resp == null) {
                return Collections.emptyMap();
            }
            List<ApprpvalIdButton> rawList =
                    switch (approvalType) {
                        case 1 -> resp.getToDoList();
                        case 2 -> resp.getDoneList();
                        case 3 -> resp.getAllList();
                        default -> resp.getAllList();
                    };
            return parseButtonsByRecord(rawList);
        } catch (Exception ex) {
            log.warn(
                    "获取审批链按钮失败: moduleId={}, type={}, err={}",
                    rootModuleId,
                    approvalType,
                    ex.getMessage());
            return Collections.emptyMap();
        }
    }

    // ==================== 3. 独立解析器：行级数据权限 (Data Permission Filters) ====================

    /** 根据当前用户登录角色解析行级数据权限过滤项 (AND 组合) */
    public List<FilterItem> buildDataPermissionFilters(ModuleTreeDTO moduleTree) {
        Long roleId = UserContext.getRoleId();
        if (roleId == null || openRolePermissionApi == null) {
            return Collections.emptyList();
        }

        List<DataRightConfigResp> permDetails;
        try {
            permDetails = openRolePermissionApi.getRoleDataPermissionDetail(roleId);
        } catch (Exception ex) {
            log.warn("拉取角色数据权限详情失败: roleId={}, err={}", roleId, ex.getMessage());
            return Collections.emptyList();
        }

        if (CollectionUtils.isEmpty(permDetails)) {
            return Collections.emptyList();
        }

        // 构建物理表名到所属模块 ID 集合的映射 (支持同一个物理表挂载在多个模块下的场景)
        Map<String, Set<Long>> tableToModuleIdsMap = new HashMap<>();
        if (moduleTree != null) {
            if (moduleTree.getRootModule() != null
                    && moduleTree.getRootModule().getPrimaryTable() != null
                    && moduleTree.getRootModule().getModuleId() != null) {
                tableToModuleIdsMap
                        .computeIfAbsent(
                                moduleTree.getRootModule().getPrimaryTable().trim().toLowerCase(),
                                k -> new LinkedHashSet<>())
                        .add(moduleTree.getRootModule().getModuleId());
            }
            if (moduleTree.getModuleMap() != null) {
                for (Map.Entry<Long, com.jdec.platform.config.api.dto.common.ModuleInfo> entry :
                        moduleTree.getModuleMap().entrySet()) {
                    if (entry.getValue() != null
                            && entry.getValue().getPrimaryTable() != null
                            && entry.getKey() != null) {
                        tableToModuleIdsMap
                                .computeIfAbsent(
                                        entry.getValue().getPrimaryTable().trim().toLowerCase(),
                                        k -> new LinkedHashSet<>())
                                .add(entry.getKey());
                    }
                }
            }
        }

        List<FilterItem> filters = new ArrayList<>();
        for (DataRightConfigResp tableResp : permDetails) {
            if (tableResp == null
                    || tableResp.getTableName() == null
                    || tableResp.getTableName().isBlank()) {
                continue;
            }
            String tableName = tableResp.getTableName().trim();
            Set<Long> targetModuleIds = tableToModuleIdsMap.get(tableName.toLowerCase());
            // 严格过滤：仅保留 moduleTree 中引用的表，没有引用的表坚决不加入 filter，避免跨模块表污染
            if (targetModuleIds == null || targetModuleIds.isEmpty()) {
                continue;
            }

            List<DataRightConfigResp.DataConfig> dataList = tableResp.getDataList();
            if (CollectionUtils.isEmpty(dataList)) {
                continue;
            }

            // 1. 若存在节点级全选 (permissionCheckAll == 1)，该表完全放行
            boolean isPermissionCheckAll =
                    dataList.stream()
                            .anyMatch(
                                    d ->
                                            d != null
                                                    && Integer.valueOf(1)
                                                            .equals(d.getPermissionCheckAll()));
            if (isPermissionCheckAll) {
                continue;
            }

            // 2. 遍历该表下的主体数据配置，各主体数据权限为 AND 关系依次拼接
            for (DataRightConfigResp.DataConfig config : dataList) {
                if (config == null) {
                    continue;
                }
                Long subjectId = config.getSubjectId();
                Integer subjectCheckAll = config.getSubjectCheckAll();
                String columnName = config.getColumnName();
                List<String> bizNames = config.getBizNames();

                for (Long targetModuleId : targetModuleIds) {
                    // 2.1 主体全选：利用返回的 subjectId 过滤 subject_id，不对业务字段 columnName 限制
                    if (Integer.valueOf(1).equals(subjectCheckAll)) {
                        if (subjectId != null) {
                            FilterItem item =
                                    FilterItem.eq(tableName, SUBJECT_ID_COLUMN, subjectId);
                            item.setModuleId(targetModuleId);
                            filters.add(item);
                        }
                        continue;
                    }

                    // 2.2 主体未全选：按主体 ID 过滤 subject_id，并且对具体业务字段 columnName 限制业务值范围 bizNames
                    if (subjectId != null) {
                        FilterItem subjectItem =
                                FilterItem.eq(tableName, SUBJECT_ID_COLUMN, subjectId);
                        subjectItem.setModuleId(targetModuleId);
                        filters.add(subjectItem);
                    }
                    if (columnName != null && !columnName.isBlank()) {
                        List<String> effectiveBizNames =
                                bizNames != null ? bizNames : Collections.emptyList();
                        FilterItem columnItem =
                                FilterItem.in(tableName, columnName.trim(), effectiveBizNames);
                        columnItem.setModuleId(targetModuleId);
                        filters.add(columnItem);
                    }
                }
            }
        }
        return filters;
    }

    /** 调用 DataApi 通过物理表条件查询抽出符合数据权限单据的根主表 business_no 列表 (distinct, active = 1) */
    public List<String> queryDataPermissionRecordIds(
            ModuleTreeDTO moduleTree, List<FilterItem> dataPermFilters) {
        if (dataApi == null || moduleTree == null || moduleTree.getRootModule() == null) {
            return Collections.emptyList();
        }
        if (CollectionUtils.isEmpty(dataPermFilters)) {
            return Collections.emptyList();
        }
        Long rootModuleId = moduleTree.getRootModule().getModuleId();
        String primaryTable = moduleTree.getRootModule().getPrimaryTable();
        if (rootModuleId == null || primaryTable == null || primaryTable.isBlank()) {
            return Collections.emptyList();
        }

        // 追加 active = 1 条件
        List<FilterItem> effectiveFilters = new ArrayList<>(dataPermFilters);
        FilterItem activeItem = FilterItem.eq(primaryTable, ACTIVE_COLUMN, 1);
        activeItem.setModuleId(rootModuleId);
        effectiveFilters.add(activeItem);

        try {
            // 优化：数据权限在大多数场景下仅基于主表（如 created_by、dept_id），直接走轻量单表查询，跳过整树元数据加载与复杂装配
            if (isSingleTableOnly(effectiveFilters, primaryTable, rootModuleId)) {
                com.jdec.platform.data.api.dto.request.SingleTableQueryReq singleReq =
                        com.jdec.platform.data.api.dto.request.SingleTableQueryReq.of(
                                primaryTable, BUSINESS_NO_COLUMN);
                singleReq.setFilters(effectiveFilters);
                singleReq.setDistinct(true);
                List<String> singleNos = dataApi.querySingleTableColumn(singleReq, String.class);
                return (singleNos != null && !singleNos.isEmpty())
                        ? new ArrayList<>(new LinkedHashSet<>(singleNos))
                        : Collections.emptyList();
            }

            // 若涉及子表数据权限（子表影响主表数据），退回通过整树引擎进行级联关联推导
            com.jdec.platform.data.api.dto.request.QueryReq req =
                    com.jdec.platform.data.api.dto.request.QueryReq.builder()
                            .moduleId(rootModuleId)
                            .tables(
                                    List.of(
                                            com.jdec.platform.data.api.dto.request.TableSpec
                                                    .builder()
                                                    .moduleId(rootModuleId)
                                                    .tableName(primaryTable)
                                                    .columns(List.of(BUSINESS_NO_COLUMN))
                                                    .build()))
                            .filters(effectiveFilters)
                            .build();

            com.jdec.platform.data.api.dto.common.DataBlock block = dataApi.queryList(req);
            if (block == null || CollectionUtils.isEmpty(block.getRecords())) {
                return Collections.emptyList();
            }

            Set<String> distinctNos = new LinkedHashSet<>();
            for (com.jdec.platform.data.api.dto.common.DataRow row : block.getRecords()) {
                if (row == null) {
                    continue;
                }
                String no = row.getString(primaryTable, BUSINESS_NO_COLUMN);
                if (no != null && !no.isBlank()) {
                    distinctNos.add(no.trim());
                }
            }
            return new ArrayList<>(distinctNos);
        } catch (Exception ex) {
            log.warn(
                    "通过 DataApi 抽取数据权限 business_no 失败: moduleId={}, err={}",
                    rootModuleId,
                    ex.getMessage(),
                    ex);
            return Collections.emptyList();
        }
    }

    // ==================== 4. 核心过滤组合构建器 ====================

    /** 核心过滤构建：有审批链场景 (自动计算由我创建与数据权限的 business_no 集合) */
    public ApprovalFilterItem buildApprovalFilterItem(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<String, List<Map<String, Object>>> btnGroupMap) {
        Integer targetStatus = (approvalType != null && approvalType == 1) ? 1 : null;
        List<String> myBusinessNos =
                (approvalType != null && approvalType == 2)
                        ? Collections.emptyList()
                        : queryMyCreatedBusinessNos(rootFieldIds, targetStatus);

        List<FilterItem> dataPermFilters =
                (approvalType != null && approvalType == 3)
                        ? buildDataPermissionFilters(
                                rootFieldIds != null ? rootFieldIds.tree() : null)
                        : Collections.emptyList();

        List<String> dataPermBusinessNos =
                (approvalType != null && approvalType == 3)
                        ? queryDataPermissionRecordIds(
                                rootFieldIds != null ? rootFieldIds.tree() : null, dataPermFilters)
                        : Collections.emptyList();

        return buildApprovalFilterItem(
                approvalType, rootFieldIds, btnGroupMap, myBusinessNos, dataPermBusinessNos);
    }

    /** 核心过滤构建：复用预先计算好的单据集合，精准组装最终过滤项 */
    public ApprovalFilterItem buildApprovalFilterItem(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<String, List<Map<String, Object>>> btnGroupMap,
            List<String> myBusinessNos,
            List<String> dataPermBusinessNos) {

        Set<String> approvalBusinessNos =
                btnGroupMap != null ? btnGroupMap.keySet() : Collections.emptySet();

        // 1. 已办 (type = 2)：仅过滤审批已办 business_no 集合，AND active = 1（不考虑 created_by 与数据权限）
        if (approvalType != null && approvalType == 2) {
            if (approvalBusinessNos.isEmpty()) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            return assembleResult(rootFieldIds, approvalBusinessNos, btnGroupMap);
        }

        // 2. 待办 (type = 1)：[审批待办] OR [created_by = :me]
        if (approvalType != null && approvalType == 1) {
            Set<String> mergedTodoNos = new LinkedHashSet<>(approvalBusinessNos);
            if (!CollectionUtils.isEmpty(myBusinessNos)) {
                mergedTodoNos.addAll(myBusinessNos);
            }
            if (mergedTodoNos.isEmpty()) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            return assembleResult(rootFieldIds, mergedTodoNos, btnGroupMap);
        }

        // 3. 全部 (type = 3)：[审批全部] OR [created_by = :me] OR [数据权限]
        if (approvalType != null && approvalType == 3) {
            Set<String> mergedAllNos = new LinkedHashSet<>(approvalBusinessNos);
            if (!CollectionUtils.isEmpty(myBusinessNos)) {
                mergedAllNos.addAll(myBusinessNos);
            }
            if (!CollectionUtils.isEmpty(dataPermBusinessNos)) {
                mergedAllNos.addAll(dataPermBusinessNos);
            }
            if (mergedAllNos.isEmpty()) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            return assembleResult(rootFieldIds, mergedAllNos, btnGroupMap);
        }

        // 4. 缺省兜底（未指定具体 approvalType）：走无审批链逻辑
        return buildNoApprovalFilterItem(rootFieldIds);
    }

    /** 无审批链场景过滤构建：[created_by = :me] OR [数据权限]，最后 AND active = 1 */
    public ApprovalFilterItem buildNoApprovalFilterItem(RootModuleFieldIds rootFieldIds) {
        List<String> myBusinessNos = queryMyCreatedBusinessNos(rootFieldIds);
        List<FilterItem> dataPermFilters =
                buildDataPermissionFilters(rootFieldIds != null ? rootFieldIds.tree() : null);
        List<String> dataPermBusinessNos =
                queryDataPermissionRecordIds(
                        rootFieldIds != null ? rootFieldIds.tree() : null, dataPermFilters);

        Set<String> mergedNos = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(myBusinessNos)) {
            mergedNos.addAll(myBusinessNos);
        }
        if (!CollectionUtils.isEmpty(dataPermBusinessNos)) {
            mergedNos.addAll(dataPermBusinessNos);
        }

        // 若双方集合均为空，短路为空
        if (mergedNos.isEmpty()) {
            return new ApprovalFilterItem(Collections.emptyList(), true, Collections.emptyMap());
        }

        return assembleResult(rootFieldIds, mergedNos, Collections.emptyMap());
    }

    /** 统一组装结果：主表 business_no IN (:businessNos) AND 主表 active = 1 */
    private ApprovalFilterItem assembleResult(
            RootModuleFieldIds rootFieldIds,
            Collection<String> businessNos,
            Map<String, List<Map<String, Object>>> btnMap) {
        FilterItem businessNoFilter = buildApprovalRecordFilter(rootFieldIds, businessNos);
        FilterItem activeFilter = buildActiveFilter(rootFieldIds);

        List<FilterItem> filters = new ArrayList<>();
        if (businessNoFilter != null) {
            filters.add(businessNoFilter);
        }
        if (activeFilter != null) {
            filters.add(activeFilter);
        }
        return new ApprovalFilterItem(filters, false, btnMap);
    }

    /** 构建主表 business_no IN (:businessNos) 过滤项 */
    public FilterItem buildApprovalRecordFilter(
            RootModuleFieldIds rootFieldIds, Collection<String> businessNos) {
        if (CollectionUtils.isEmpty(businessNos)) {
            return null;
        }
        if (rootFieldIds != null
                && rootFieldIds.tree() != null
                && rootFieldIds.businessNoField() != null) {
            return PhysicalQueryMapper.filter(
                    rootFieldIds.tree(),
                    rootFieldIds.businessNoField().fieldId(),
                    "IN",
                    new ArrayList<>(businessNos));
        }
        String primaryTable =
                rootFieldIds != null
                                && rootFieldIds.tree() != null
                                && rootFieldIds.tree().getRootModule() != null
                        ? rootFieldIds.tree().getRootModule().getPrimaryTable()
                        : null;
        Long rootModuleId =
                rootFieldIds != null
                                && rootFieldIds.tree() != null
                                && rootFieldIds.tree().getRootModule() != null
                        ? rootFieldIds.tree().getRootModule().getModuleId()
                        : null;
        if (primaryTable != null && !primaryTable.isBlank()) {
            FilterItem item =
                    FilterItem.in(primaryTable, BUSINESS_NO_COLUMN, new ArrayList<>(businessNos));
            item.setModuleId(rootModuleId);
            return item;
        }
        return null;
    }

    /** 构建主表 active = 1 过滤项 */
    public FilterItem buildActiveFilter(RootModuleFieldIds rootFieldIds) {
        if (rootFieldIds != null
                && rootFieldIds.tree() != null
                && rootFieldIds.activeField() != null) {
            return PhysicalQueryMapper.filter(
                    rootFieldIds.tree(), rootFieldIds.activeField().fieldId(), "EQ", 1);
        }
        String primaryTable =
                rootFieldIds != null
                                && rootFieldIds.tree() != null
                                && rootFieldIds.tree().getRootModule() != null
                        ? rootFieldIds.tree().getRootModule().getPrimaryTable()
                        : null;
        Long rootModuleId =
                rootFieldIds != null
                                && rootFieldIds.tree() != null
                                && rootFieldIds.tree().getRootModule() != null
                        ? rootFieldIds.tree().getRootModule().getModuleId()
                        : null;
        if (primaryTable != null && !primaryTable.isBlank()) {
            FilterItem item = FilterItem.eq(primaryTable, ACTIVE_COLUMN, 1);
            item.setModuleId(rootModuleId);
            return item;
        }
        return null;
    }

    /** 解析审批流按钮数据结构，返回以 business_no 为 key 的按钮映射 */
    private Map<String, List<Map<String, Object>>> parseButtonsByRecord(
            List<ApprpvalIdButton> rawList) {
        if (CollectionUtils.isEmpty(rawList)) {
            return Collections.emptyMap();
        }
        Map<String, List<Map<String, Object>>> recordBtnMap = new LinkedHashMap<>();
        for (ApprpvalIdButton rawBtn : rawList) {
            if (rawBtn == null) {
                continue;
            }
            String key =
                    (rawBtn.getBusinessNo() != null && !rawBtn.getBusinessNo().isBlank())
                            ? rawBtn.getBusinessNo().trim()
                            : (rawBtn.getRecordId() != null
                                    ? String.valueOf(rawBtn.getRecordId())
                                    : null);
            if (key == null) {
                continue;
            }
            Map<String, Object> btnInfo = new LinkedHashMap<>();
            btnInfo.put(
                    "buttonKey", rawBtn.getButtonKey() != null ? rawBtn.getButtonKey() : "view");
            btnInfo.put(
                    "buttonName", rawBtn.getButtonName() != null ? rawBtn.getButtonName() : "查阅");
            recordBtnMap.computeIfAbsent(key, k -> new ArrayList<>()).add(btnInfo);
        }
        return recordBtnMap;
    }

    /** 提取根模块的字段引用元数据 (id, business_no, created_by, active) */
    public RootModuleFieldIds extractRootModuleFieldIds(
            Long rootModuleId, ModuleTreeDTO inMemoryModuleTree) {
        if (rootModuleId == null) {
            return new RootModuleFieldIds(null, null, null, null, null, null);
        }
        ModuleTreeDTO moduleTree =
                inMemoryModuleTree != null
                        ? inMemoryModuleTree
                        : configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);

        PhysicalQueryMapper.PhysicalFieldRef idField = null;
        PhysicalQueryMapper.PhysicalFieldRef businessNoField = null;
        PhysicalQueryMapper.PhysicalFieldRef createdByField = null;
        PhysicalQueryMapper.PhysicalFieldRef businessStatusField = null;
        PhysicalQueryMapper.PhysicalFieldRef activeField = null;

        if (moduleTree != null && moduleTree.getRootModule() != null) {
            List<ModuleFieldDTO> fields = moduleTree.getRootModule().getFields();
            if (fields != null) {
                for (ModuleFieldDTO f : fields) {
                    String col = f.getColumnName();
                    if ("id".equalsIgnoreCase(col)) {
                        idField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    } else if ("business_no".equalsIgnoreCase(col)
                            || "businessNo".equalsIgnoreCase(col)
                            || "business_number".equalsIgnoreCase(col)) {
                        businessNoField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    } else if ("created_by".equalsIgnoreCase(col)
                            || "createdBy".equalsIgnoreCase(col)) {
                        createdByField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    } else if ("business_status".equalsIgnoreCase(col)
                            || "businessStatus".equalsIgnoreCase(col)) {
                        businessStatusField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    } else if ("active".equalsIgnoreCase(col)) {
                        activeField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    }
                }
            }
        }
        return new RootModuleFieldIds(
                moduleTree,
                idField,
                businessNoField,
                createdByField,
                businessStatusField,
                activeField);
    }

    /** 判断过滤条件是否仅涉及主表字段（用于决定是否可直接降级为轻量单表查询） */
    private boolean isSingleTableOnly(
            List<FilterItem> filters, String primaryTable, Long rootModuleId) {
        if (CollectionUtils.isEmpty(filters)) {
            return true;
        }
        for (FilterItem f : filters) {
            if (f == null) {
                continue;
            }
            if (f.getModuleId() != null && !f.getModuleId().equals(rootModuleId)) {
                return false;
            }
            if (f.getTableName() != null
                    && !f.getTableName().isBlank()
                    && !f.getTableName().equalsIgnoreCase(primaryTable)) {
                return false;
            }
            if (!CollectionUtils.isEmpty(f.getChildren())) {
                if (!isSingleTableOnly(f.getChildren(), primaryTable, rootModuleId)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 审批流与通用过滤解析结果单项（包含过滤项集合、是否短路为空、单据按钮映射） */
    public record ApprovalFilterItem(
            List<FilterItem> filters,
            boolean shortCircuitEmpty,
            Map<String, List<Map<String, Object>>> btnMap) {}

    /** 根模块关键字段引用 */
    public record RootModuleFieldIds(
            ModuleTreeDTO tree,
            PhysicalQueryMapper.PhysicalFieldRef idField,
            PhysicalQueryMapper.PhysicalFieldRef businessNoField,
            PhysicalQueryMapper.PhysicalFieldRef createdByField,
            PhysicalQueryMapper.PhysicalFieldRef businessStatusField,
            PhysicalQueryMapper.PhysicalFieldRef activeField) {}

    /** 全量角标统计过滤结果（待办/已办/全部） */
    public record AllApprovalCountFilters(
            boolean enabled,
            ApprovalFilterItem todoFilter,
            ApprovalFilterItem doneFilter,
            ApprovalFilterItem allFilter) {}
}
