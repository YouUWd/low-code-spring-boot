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
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.data.api.model.predicate.Predicates;
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
 * <p>职责划分清晰：由 3 组独立的过滤器解析后，通过顶层组合器（Combiner）进行逻辑组合：
 *
 * <ul>
 *   <li>1. 待办 (type = 1): (我创建的草稿) OR (审批待办单据)
 *   <li>2. 已办 (type = 2): 审批已办单据 (id IN :doneRecordIds)
 *   <li>3. 全部 (type = 3): (我创建的草稿) OR (审批全部单据) OR (数据权限单据)
 *   <li>4. 未开启审批流: 行级数据权限过滤
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

        // 2. 提取根模块元数据
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);

        // 3. 独立拉取审批流按钮与单据
        Map<Long, List<Map<String, Object>>> btnMap =
                queryApprovalButtons(rootModuleId, approvalType);

        // 4. 组装并组合 3 组独立 filter
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
            // 1. 一次性获取审批流按钮与分组列表
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

            // 2. 独立解析行级数据权限条件，并提取数据权限匹配的主表 ID 列表（供 type=3 并集使用）
            List<FilterItem> dataPermFilters = buildDataPermissionFilters(rootFieldIds.tree());
            List<Long> dataPermRecordIds =
                    queryDataPermissionRecordIds(rootFieldIds.tree(), dataPermFilters);

            // 3. 分别构建待办、已办、全部的过滤项
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

    // ==================== 1. 独立解析器：我创建的草稿 (Draft Filters) ====================

    /** 构建当前用户创建的草稿过滤项列表：created_by = :me AND business_status = 1 */
    public List<FilterItem> buildDraftFilters(RootModuleFieldIds rootFieldIds) {
        if (rootFieldIds == null || rootFieldIds.tree() == null) {
            return Collections.emptyList();
        }
        Long currentUserId =
                UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
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

    // ==================== 2. 独立解析器：审批流单据过滤 (Approval Record Filters) ====================

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

    /** 构建审批流单据过滤项：id IN (:recordIds) */
    public FilterItem buildApprovalRecordFilter(
            RootModuleFieldIds rootFieldIds, Set<Long> recordIds) {
        if (rootFieldIds == null
                || rootFieldIds.tree() == null
                || rootFieldIds.idField() == null
                || CollectionUtils.isEmpty(recordIds)) {
            return null;
        }
        return PhysicalQueryMapper.filter(
                rootFieldIds.tree(),
                rootFieldIds.idField().fieldId(),
                "IN",
                new ArrayList<>(recordIds));
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

    /** 构建数据权限匹配的单据 ID 过滤项：id IN (:dataPermRecordIds) */
    public FilterItem buildDataPermRecordFilter(
            RootModuleFieldIds rootFieldIds, List<Long> dataPermRecordIds) {
        if (rootFieldIds == null
                || rootFieldIds.tree() == null
                || rootFieldIds.idField() == null
                || CollectionUtils.isEmpty(dataPermRecordIds)) {
            return null;
        }
        return PhysicalQueryMapper.filter(
                rootFieldIds.tree(),
                rootFieldIds.idField().fieldId(),
                "IN",
                new ArrayList<>(dataPermRecordIds));
    }

    // ==================== 4. 顶层组合器：纯粹且清晰的组合逻辑 (Combiner) ====================

    /** 核心过滤构建：组装指定 approvalType 对应的过滤项、数据权限与按钮（自动计算数据权限） */
    public ApprovalFilterItem buildApprovalFilterItem(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {
        ModuleTreeDTO tree = rootFieldIds != null ? rootFieldIds.tree() : null;
        // 待办(1)和已办(2)不考虑数据权限；仅全部(3)考虑数据权限
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

    /** 核心过滤构建：复用预先计算好的数据权限过滤项与数据权限主表 ID 列表，实现 3 组独立 filter 的清晰组合 */
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

        Set<Long> approvalRecordIds =
                btnGroupMap != null ? btnGroupMap.keySet() : Collections.emptySet();

        // 1. 独立解析：【草稿过滤器】 (created_by = me AND business_status = 1)
        List<FilterItem> draftFilters = buildDraftFilters(rootFieldIds);

        // 2. 独立解析：【审批单据过滤器】 (id IN :approvalRecordIds)
        FilterItem approvalFilter = buildApprovalRecordFilter(rootFieldIds, approvalRecordIds);

        // 3. 独立解析：【数据权限单据过滤器】 (id IN :dataPermRecordIds)
        FilterItem dataPermFilter = buildDataPermRecordFilter(rootFieldIds, dataPermRecordIds);

        // 4. 场景组合
        // 【待办 (1)】：(我创建的草稿) OR (审批待办单据)
        if (approvalType == 1) {
            if (approvalFilter == null && draftFilters.isEmpty()) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            List<FilterItem> combined = combineOrBranches(draftFilters, approvalFilter, null);
            return new ApprovalFilterItem(combined, false, btnGroupMap);
        }

        // 【已办 (2)】：审批已办单据 (id IN :doneRecordIds)
        if (approvalType == 2) {
            if (approvalFilter == null) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            return new ApprovalFilterItem(List.of(approvalFilter), false, btnGroupMap);
        }

        // 【全部 (3)】：(我创建的草稿) OR (审批全部单据) OR (数据权限单据)
        if (approvalType == 3) {
            if (approvalFilter == null && dataPermFilter == null && draftFilters.isEmpty()) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            List<FilterItem> combined =
                    combineOrBranches(draftFilters, approvalFilter, dataPermFilter);
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

    /** 将 (我创建的草稿)、(审批单据分支) 与 (数据权限单据分支) 3 组独立的过滤器组合为顶层 OR 树结构 */
    private List<FilterItem> combineOrBranches(
            List<FilterItem> draftItems, FilterItem approvalBranch, FilterItem dataPermBranch) {
        List<Predicate> branchPredicates = new ArrayList<>();

        // 1. 草稿分支：created_by == me AND business_status == 1
        if (!CollectionUtils.isEmpty(draftItems)) {
            List<Predicate> draftPreds = new ArrayList<>();
            for (FilterItem di : draftItems) {
                if (di != null) {
                    Predicate p = di.toPredicate();
                    if (p != null) {
                        draftPreds.add(p);
                    }
                }
            }
            if (!draftPreds.isEmpty()) {
                branchPredicates.add(Predicates.and(draftPreds));
            }
        }

        // 2. 审批流单据分支：id IN (:approvalRecordIds)
        if (approvalBranch != null) {
            Predicate p = approvalBranch.toPredicate();
            if (p != null) {
                branchPredicates.add(p);
            }
        }

        // 3. 数据权限单据分支：id IN (:dataPermRecordIds)
        if (dataPermBranch != null) {
            Predicate p = dataPermBranch.toPredicate();
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

        // 4. 构造标准 OR 复合树
        Predicate rootOr = Predicates.or(branchPredicates);
        return Collections.singletonList(FilterItem.fromPredicate(rootOr));
    }

    private Map<Long, List<Map<String, Object>>> parseButtonsByRecord(
            List<ApprpvalIdButton> rawList) {
        if (CollectionUtils.isEmpty(rawList)) {
            return Collections.emptyMap();
        }
        Map<Long, List<Map<String, Object>>> recordBtnMap = new LinkedHashMap<>();
        for (ApprpvalIdButton rawBtn : rawList) {
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
