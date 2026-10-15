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
 * <p>职责：保证搜索 (Search/Query)、计数 (Count) 与候选项 (Option) 遵循完全一致的过滤规则：
 *
 * <ul>
 *   <li>1. 待办 (type = 1): (我创建的草稿) OR (审批待办单据) OR (数据权限)
 *   <li>2. 已办 (type = 2): (id IN :doneRecordIds) AND (数据权限)
 *   <li>3. 全部 (type = 3): (我创建的草稿) OR (审批全部单据) OR (数据权限)
 *   <li>4. 数据权限 (Data Permissions): 根据当前登录角色配置，支持主体全选 (subjectCheckAll) 与字段业务值限制
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonFilterResolver {

    public static final String SUBJECT_ID_COLUMN = "subject_id";

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
        // 1. 若未指定审批类型或未开启审批链，直接返回数据权限过滤结果
        if (rootModuleId == null || approvalType == null || !isApprovalChainEnabled(rootModuleId)) {
            return new ApprovalFilterItem(
                    buildDataPermissionFilters(moduleTree), false, Collections.emptyMap());
        }

        // 2. 解析审批流按钮与过滤 (已在内部融合数据权限)
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        Map<Long, List<Map<String, Object>>> btnMap =
                queryApprovalButtons(rootModuleId, approvalType);
        return buildApprovalFilterItem(approvalType, rootFieldIds, btnMap);
    }

    /** 根据当前用户登录角色解析行级数据权限过滤项 (AND 组合) */
    public List<FilterItem> buildDataPermissionFilters() {
        return buildDataPermissionFilters((ModuleTreeDTO) null);
    }

    /** 根据当前用户登录角色与指定根模块解析行级数据权限过滤项 (AND 组合) */
    public List<FilterItem> buildDataPermissionFilters(Long rootModuleId) {
        if (rootModuleId == null) {
            return buildDataPermissionFilters((ModuleTreeDTO) null);
        }
        ModuleTreeDTO moduleTree =
                configApi != null
                        ? configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId)
                        : null;
        return buildDataPermissionFilters(moduleTree);
    }

    /**
     * 根据当前用户登录角色解析行级数据权限过滤项 (AND 组合)
     *
     * <p>若提供 moduleTree，会解析出各物理表对应的所属 moduleId，赋给 FilterItem， 避免非主表过滤条件被错误拼接到主表根查询的顶层 WHERE 中引发
     * Unknown column 异常。
     */
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
            // 若当前模块树中找不到该表，保留 null 以便未在树中显式声明主表时走通用回退
            Collection<Long> effectiveModuleIds =
                    (targetModuleIds != null && !targetModuleIds.isEmpty())
                            ? targetModuleIds
                            : Collections.singletonList(null);

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

                for (Long targetModuleId : effectiveModuleIds) {
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

                    // 2.2 非主体全选 (subjectCheckAll == 0)：按业务名称过滤
                    if (subjectId != null) {
                        FilterItem item = FilterItem.eq(tableName, SUBJECT_ID_COLUMN, subjectId);
                        item.setModuleId(targetModuleId);
                        filters.add(item);
                    }
                    if (columnName != null && !columnName.isBlank()) {
                        List<String> validBizNames =
                                (bizNames != null) ? bizNames : Collections.emptyList();
                        // bizNames 为空时，生成 IN 空列表，SQL 编译层按 1=0 或 IN () 查不出数据
                        FilterItem item =
                                FilterItem.in(tableName, columnName.trim(), validBizNames);
                        item.setModuleId(targetModuleId);
                        filters.add(item);
                    }
                }
            }
        }
        return filters;
    }

    /**
     * 一次性拉取全部审批流过滤，供计数服务 (CommonApprovalServiceImpl) 分别统计待办/已办/全部 调用
     * approvalEngine.getApprovalListButton(rootModuleId) 获取分好组的待办、已办、全部列表
     */
    public AllApprovalCountFilters resolveAllApprovalCountsFilters(
            Long rootModuleId, ModuleTreeDTO moduleTree) {
        if (rootModuleId == null || !isApprovalChainEnabled(rootModuleId)) {
            return new AllApprovalCountFilters(false, null, null, null);
        }

        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        try {
            // 1. 一次性获取审批流按钮映射
            com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp resp =
                    approvalEngine.getApprovalListButton(rootModuleId);
            Map<Long, List<Map<String, Object>>> todoBtns =
                    resp != null
                            ? parseButtonsByRecord(resp.getToDoList())
                            : Collections.emptyMap();
            Map<Long, List<Map<String, Object>>> doneBtns =
                    resp != null
                            ? parseButtonsByRecord(resp.getDoneList())
                            : Collections.emptyMap();
            Map<Long, List<Map<String, Object>>> allBtns =
                    resp != null ? parseButtonsByRecord(resp.getAllList()) : Collections.emptyMap();

            // 2. 一次性拉取数据权限过滤条件，并通过 DataApi 仅抽取一次轻量的主表 ID 列表 (仅查一次，避免重复调用接口与多次SQL查询)
            List<FilterItem> dataPermFilters = buildDataPermissionFilters(rootFieldIds.tree());
            List<Long> dataPermRecordIds =
                    queryDataPermissionRecordIds(rootFieldIds.tree(), dataPermFilters);

            // 3. 复用同一批解析好的数据权限条件与 ID 集合，分别构建待办、已办、全部的过滤项
            return new AllApprovalCountFilters(
                    true,
                    buildApprovalFilterItem(
                            1, rootFieldIds, todoBtns, dataPermFilters, dataPermRecordIds),
                    buildApprovalFilterItem(
                            2, rootFieldIds, doneBtns, dataPermFilters, dataPermRecordIds),
                    buildApprovalFilterItem(
                            3, rootFieldIds, allBtns, dataPermFilters, dataPermRecordIds));
        } catch (Exception ex) {
            log.warn("批量获取审批链按钮失败: moduleId={}, err={}", rootModuleId, ex.getMessage());
            return new AllApprovalCountFilters(false, null, null, null);
        }
    }

    /** 向审批引擎查询指定审批类型的记录与按钮列表 */
    public Map<Long, List<Map<String, Object>>> queryApprovalButtons(
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

    /** 核心过滤构建：组装指定 approvalType 对应的物理表过滤项、数据权限与按钮（自动解析数据权限） */
    public ApprovalFilterItem buildApprovalFilterItem(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {
        ModuleTreeDTO tree = rootFieldIds != null ? rootFieldIds.tree() : null;
        // 待办和已办不考虑数据权限；仅全部(3)考虑数据权限
        List<FilterItem> dataPermFilters =
                (approvalType != null && (approvalType == 1 || approvalType == 2))
                        ? Collections.emptyList()
                        : buildDataPermissionFilters(tree);
        List<Long> dataPermRecordIds =
                (approvalType != null && approvalType == 3)
                        ? queryDataPermissionRecordIds(tree, dataPermFilters)
                        : Collections.emptyList();
        return buildApprovalFilterItem(
                approvalType, rootFieldIds, btnGroupMap, dataPermFilters, dataPermRecordIds);
    }

    /** 核心过滤构建：复用预先计算好的数据权限过滤项与数据权限主表 ID 列表，避免多次调用接口 */
    public ApprovalFilterItem buildApprovalFilterItem(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap,
            List<FilterItem> dataPermFilters,
            List<Long> dataPermRecordIds) {
        List<FilterItem> effectiveDataPermFilters =
                dataPermFilters != null ? dataPermFilters : Collections.emptyList();

        if (approvalType == null) {
            return new ApprovalFilterItem(effectiveDataPermFilters, false, btnGroupMap);
        }

        Long currentUserId =
                UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
        Set<Long> recordIds = btnGroupMap != null ? btnGroupMap.keySet() : Collections.emptySet();
        boolean hasApprovalIds = !recordIds.isEmpty() && rootFieldIds.idField() != null;

        // 【待办 (1)】：(我创建的草稿) OR (审批待办单据)，不考虑数据权限
        if (approvalType == 1) {
            List<FilterItem> draftItems = buildDraftFilterItems(currentUserId, rootFieldIds);
            boolean hasDraft = !CollectionUtils.isEmpty(draftItems);

            if (!hasApprovalIds && !hasDraft) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }

            // 审批待办单据分支：id IN (:todoRecordIds)
            FilterItem approvalBranch =
                    hasApprovalIds
                            ? PhysicalQueryMapper.filter(
                                    rootFieldIds.tree(),
                                    rootFieldIds.idField().fieldId(),
                                    "IN",
                                    new ArrayList<>(recordIds))
                            : null;

            List<FilterItem> combined = combineOrBranches(draftItems, approvalBranch, null);
            return new ApprovalFilterItem(combined, false, btnGroupMap);
        }

        // 【已办 (2)】：id IN (:doneRecordIds)，不考虑数据权限
        if (approvalType == 2) {
            if (!hasApprovalIds) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            FilterItem doneFilter =
                    PhysicalQueryMapper.filter(
                            rootFieldIds.tree(),
                            rootFieldIds.idField().fieldId(),
                            "IN",
                            new ArrayList<>(recordIds));
            return new ApprovalFilterItem(List.of(doneFilter), false, btnGroupMap);
        }

        // 【全部 (3)】：(我创建的草稿) OR (审批全部单据) OR (数据权限单据)
        if (approvalType == 3) {
            List<FilterItem> draftItems = buildDraftFilterItems(currentUserId, rootFieldIds);
            boolean hasDraft = !CollectionUtils.isEmpty(draftItems);

            boolean hasDataPermIds =
                    !CollectionUtils.isEmpty(dataPermRecordIds) && rootFieldIds.idField() != null;

            if (!hasApprovalIds && !hasDataPermIds && !hasDraft) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }

            // 1. 审批单据分支：id IN (:approvalRecordIds)
            FilterItem approvalBranch =
                    hasApprovalIds
                            ? PhysicalQueryMapper.filter(
                                    rootFieldIds.tree(),
                                    rootFieldIds.idField().fieldId(),
                                    "IN",
                                    new ArrayList<>(recordIds))
                            : null;

            // 2. 数据权限单据分支：id IN (:dataPermRecordIds)
            FilterItem dataPermBranch =
                    hasDataPermIds
                            ? PhysicalQueryMapper.filter(
                                    rootFieldIds.tree(),
                                    rootFieldIds.idField().fieldId(),
                                    "IN",
                                    new ArrayList<>(dataPermRecordIds))
                            : null;

            List<FilterItem> combined =
                    combineOrBranches(draftItems, approvalBranch, dataPermBranch);
            return new ApprovalFilterItem(combined, false, btnGroupMap);
        }

        return new ApprovalFilterItem(effectiveDataPermFilters, false, btnGroupMap);
    }

    /** 调用 DataApi 通过物理表条件查询抽出符合数据权限单据的根主表 ID 列表 */
    public List<Long> queryDataPermissionRecordIds(
            ModuleTreeDTO moduleTree, List<FilterItem> dataPermFilters) {
        if (dataApi == null || moduleTree == null || moduleTree.getRootModule() == null) {
            return Collections.emptyList();
        }
        if (CollectionUtils.isEmpty(dataPermFilters)) {
            // 无数据权限配置或完全放行
            return Collections.emptyList();
        }
        Long rootModuleId = moduleTree.getRootModule().getModuleId();
        String primaryTable = moduleTree.getRootModule().getPrimaryTable();
        if (rootModuleId == null || primaryTable == null || primaryTable.isBlank()) {
            return Collections.emptyList();
        }

        try {
            com.jdec.platform.data.api.dto.request.PageQueryReq req =
                    com.jdec.platform.data.api.dto.request.PageQueryReq.builder()
                            .moduleId(rootModuleId)
                            .tables(
                                    List.of(
                                            com.jdec.platform.data.api.dto.request.TableSpec
                                                    .builder()
                                                    .moduleId(rootModuleId)
                                                    .tableName(primaryTable)
                                                    .columns(List.of("id"))
                                                    .build()))
                            .filters(dataPermFilters)
                            .pageNo(1)
                            .pageSize(10000)
                            .build();

            com.jdec.platform.data.api.dto.response.DataPage<
                            com.jdec.platform.data.api.dto.common.DataBlock>
                    page = dataApi.queryPage(req);
            if (page == null
                    || page.getData() == null
                    || CollectionUtils.isEmpty(page.getData().getRecords())) {
                return Collections.emptyList();
            }

            List<Long> resultIds = new ArrayList<>();
            for (com.jdec.platform.data.api.dto.common.DataRow row : page.getData().getRecords()) {
                if (row == null) {
                    continue;
                }
                Long id = row.getLong(primaryTable, "id");
                if (id != null) {
                    resultIds.add(id);
                }
            }
            return resultIds;
        } catch (Exception ex) {
            log.warn(
                    "通过 DataApi 抽取数据权限 ID 失败: moduleId={}, err={}",
                    rootModuleId,
                    ex.getMessage(),
                    ex);
            return Collections.emptyList();
        }
    }

    /** 将 (我创建的草稿)、(审批单据分支) 与 (数据权限单据分支) 组合为平级/嵌套的 OR 关系 */
    private List<FilterItem> combineOrBranches(
            List<FilterItem> draftItems, FilterItem approvalBranch, FilterItem dataPermBranch) {
        List<com.jdec.platform.data.api.model.predicate.Predicate> branchPredicates =
                new ArrayList<>();

        // 1. 草稿分支：created_by == me AND business_status == 1
        if (!CollectionUtils.isEmpty(draftItems)) {
            List<com.jdec.platform.data.api.model.predicate.Predicate> draftPreds =
                    new ArrayList<>();
            for (FilterItem di : draftItems) {
                if (di != null) {
                    var p = di.toPredicate();
                    if (p != null) {
                        draftPreds.add(p);
                    }
                }
            }
            if (!draftPreds.isEmpty()) {
                branchPredicates.add(
                        com.jdec.platform.data.api.model.predicate.Predicates.and(draftPreds));
            }
        }

        // 2. 审批流单据分支：id IN (:approvalRecordIds)
        if (approvalBranch != null) {
            var p = approvalBranch.toPredicate();
            if (p != null) {
                branchPredicates.add(p);
            }
        }

        // 3. 数据权限单据分支：id IN (:dataPermRecordIds)
        if (dataPermBranch != null) {
            var p = dataPermBranch.toPredicate();
            if (p != null) {
                branchPredicates.add(p);
            }
        }

        if (branchPredicates.isEmpty()) {
            return Collections.emptyList();
        }
        if (branchPredicates.size() == 1) {
            return Collections.singletonList(FilterItem.fromPredicate(branchPredicates.get(0)));
        }

        // 4. 基于全新 Predicates 构造器构建自洽 OR 复合树，并通过 fromPredicate 完美桥接
        var rootOr = com.jdec.platform.data.api.model.predicate.Predicates.or(branchPredicates);
        return Collections.singletonList(FilterItem.fromPredicate(rootOr));
    }

    private FilterItem copyFilterItem(FilterItem src) {
        if (src == null) {
            return null;
        }
        FilterItem copy = new FilterItem();
        copy.setModuleId(src.getModuleId());
        copy.setTableName(src.getTableName());
        copy.setColumnName(src.getColumnName());
        copy.setOperator(src.getOperator());
        copy.setValue(src.getValue());
        copy.setLogic(src.getLogic());
        if (src.getChildren() != null) {
            List<FilterItem> childCopies = new ArrayList<>();
            for (FilterItem c : src.getChildren()) {
                childCopies.add(copyFilterItem(c));
            }
            copy.setChildren(childCopies);
        }
        return copy;
    }

    private List<FilterItem> buildDraftFilterItems(
            Long currentUserId, RootModuleFieldIds rootFieldIds) {
        if (currentUserId == null
                || rootFieldIds.createdByField() == null
                || rootFieldIds.businessStatusField() == null) {
            return Collections.emptyList();
        }
        return List.of(
                PhysicalQueryMapper.filter(
                        rootFieldIds.tree(),
                        rootFieldIds.createdByField().fieldId(),
                        "EQ",
                        currentUserId),
                PhysicalQueryMapper.filter(
                        rootFieldIds.tree(),
                        rootFieldIds.businessStatusField().fieldId(),
                        "EQ",
                        1));
    }

    private Map<Long, List<Map<String, Object>>> parseButtonsByRecord(
            List<com.jdec.platform.approval.api.dto.ApprpvalIdButton> rawList) {
        if (CollectionUtils.isEmpty(rawList)) {
            return Collections.emptyMap();
        }
        Map<Long, List<Map<String, Object>>> recordBtnMap = new LinkedHashMap<>();
        for (com.jdec.platform.approval.api.dto.ApprpvalIdButton rawBtn : rawList) {
            if (rawBtn == null || rawBtn.getRecordId() == null) {
                continue;
            }
            Long recordId = rawBtn.getRecordId();
            Map<String, Object> btnInfo = new LinkedHashMap<>();
            btnInfo.put(
                    "buttonKey", rawBtn.getButtonKey() != null ? rawBtn.getButtonKey() : "view");
            btnInfo.put(
                    "buttonName", rawBtn.getButtonName() != null ? rawBtn.getButtonName() : "查阅");
            recordBtnMap.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
        }
        return recordBtnMap;
    }

    private Long parseRecordId(Object rIdObj) {
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

    public RootModuleFieldIds extractRootModuleFieldIds(
            Long rootModuleId, ModuleTreeDTO inMemoryModuleTree) {
        if (rootModuleId == null) {
            return new RootModuleFieldIds(null, null, null, null);
        }
        ModuleTreeDTO moduleTree =
                inMemoryModuleTree != null
                        ? inMemoryModuleTree
                        : configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);

        PhysicalQueryMapper.PhysicalFieldRef idField = null;
        PhysicalQueryMapper.PhysicalFieldRef createdByField = null;
        PhysicalQueryMapper.PhysicalFieldRef businessStatusField = null;

        if (moduleTree != null && moduleTree.getRootModule() != null) {
            List<ModuleFieldDTO> fields = moduleTree.getRootModule().getFields();
            if (fields != null) {
                for (ModuleFieldDTO f : fields) {
                    String col = f.getColumnName();
                    if ("id".equalsIgnoreCase(col)) {
                        idField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    } else if ("created_by".equalsIgnoreCase(col)
                            || "createdBy".equalsIgnoreCase(col)) {
                        createdByField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    } else if ("business_status".equalsIgnoreCase(col)
                            || "businessStatus".equalsIgnoreCase(col)) {
                        businessStatusField = PhysicalQueryMapper.resolve(moduleTree, f.getId());
                    }
                }
            }
        }
        return new RootModuleFieldIds(moduleTree, idField, createdByField, businessStatusField);
    }

    /** 审批流与通用过滤解析结果单项（包含过滤项集合、是否短路为空、单据按钮映射） */
    public record ApprovalFilterItem(
            List<FilterItem> filters,
            boolean shortCircuitEmpty,
            Map<Long, List<Map<String, Object>>> btnMap) {}

    /** 根模块关键字段引用 */
    public record RootModuleFieldIds(
            ModuleTreeDTO tree,
            PhysicalQueryMapper.PhysicalFieldRef idField,
            PhysicalQueryMapper.PhysicalFieldRef createdByField,
            PhysicalQueryMapper.PhysicalFieldRef businessStatusField) {}

    /** 全量角标统计过滤结果（待办/已办/全部） */
    public record AllApprovalCountFilters(
            boolean enabled,
            ApprovalFilterItem todoFilter,
            ApprovalFilterItem doneFilter,
            ApprovalFilterItem allFilter) {}
}
