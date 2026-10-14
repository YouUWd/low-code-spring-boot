package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.dto.ApprpvalIdButton;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenRolePermissionApi;
import com.jdec.platform.data.api.dto.request.PhysicalFilterItem;
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
    public List<PhysicalFilterItem> buildDataPermissionFilters() {
        return buildDataPermissionFilters((ModuleTreeDTO) null);
    }

    /** 根据当前用户登录角色与指定根模块解析行级数据权限过滤项 (AND 组合) */
    public List<PhysicalFilterItem> buildDataPermissionFilters(Long rootModuleId) {
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
     * <p>若提供 moduleTree，会解析出各物理表对应的所属 moduleId，赋给 PhysicalFilterItem， 避免非主表过滤条件被错误拼接到主表根查询的顶层 WHERE
     * 中引发 Unknown column 异常。
     */
    public List<PhysicalFilterItem> buildDataPermissionFilters(ModuleTreeDTO moduleTree) {
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

        List<PhysicalFilterItem> filters = new ArrayList<>();
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
                            PhysicalFilterItem item =
                                    PhysicalFilterItem.eq(tableName, SUBJECT_ID_COLUMN, subjectId);
                            item.setModuleId(targetModuleId);
                            filters.add(item);
                        }
                        continue;
                    }

                    // 2.2 非主体全选 (subjectCheckAll == 0)：按业务名称过滤
                    if (subjectId != null) {
                        PhysicalFilterItem item =
                                PhysicalFilterItem.eq(tableName, SUBJECT_ID_COLUMN, subjectId);
                        item.setModuleId(targetModuleId);
                        filters.add(item);
                    }
                    if (columnName != null && !columnName.isBlank()) {
                        List<String> validBizNames =
                                (bizNames != null) ? bizNames : Collections.emptyList();
                        // bizNames 为空时，生成 IN 空列表，SQL 编译层按 1=0 或 IN () 查不出数据
                        PhysicalFilterItem item =
                                PhysicalFilterItem.in(tableName, columnName.trim(), validBizNames);
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

            return new AllApprovalCountFilters(
                    true,
                    buildApprovalFilterItem(1, rootFieldIds, todoBtns),
                    buildApprovalFilterItem(2, rootFieldIds, doneBtns),
                    buildApprovalFilterItem(3, rootFieldIds, allBtns));
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

    /** 核心过滤构建：组装指定 approvalType 对应的物理表过滤项、数据权限与按钮 */
    public ApprovalFilterItem buildApprovalFilterItem(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {
        ModuleTreeDTO tree = rootFieldIds != null ? rootFieldIds.tree() : null;
        List<PhysicalFilterItem> dataPermFilters = buildDataPermissionFilters(tree);

        if (approvalType == null) {
            return new ApprovalFilterItem(dataPermFilters, false, btnGroupMap);
        }

        Long currentUserId =
                UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
        Set<Long> recordIds = btnGroupMap != null ? btnGroupMap.keySet() : Collections.emptySet();
        boolean hasApprovalIds = !recordIds.isEmpty() && rootFieldIds.idField() != null;

        // 【待办 (1)】 与 【全部 (3)】：(我创建的草稿) OR (审批待办/全部单据) OR (数据权限)
        if (approvalType == 1 || approvalType == 3) {
            List<PhysicalFilterItem> draftItems =
                    buildDraftFilterItems(currentUserId, rootFieldIds);
            boolean hasDraft = !CollectionUtils.isEmpty(draftItems);
            boolean hasDataPerm = !CollectionUtils.isEmpty(dataPermFilters);

            if (!hasApprovalIds && !hasDraft && !hasDataPerm) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }

            PhysicalFilterItem approvalBranch =
                    hasApprovalIds
                            ? PhysicalQueryMapper.filter(
                                    rootFieldIds.tree(),
                                    rootFieldIds.idField().fieldId(),
                                    "IN",
                                    new ArrayList<>(recordIds))
                            : null;

            List<PhysicalFilterItem> combined =
                    combineOrBranches(draftItems, approvalBranch, dataPermFilters);
            return new ApprovalFilterItem(combined, false, btnGroupMap);
        }

        // 【已办 (2)】：id IN doneRecordIds AND 数据权限
        if (approvalType == 2) {
            if (!hasApprovalIds) {
                return new ApprovalFilterItem(Collections.emptyList(), true, btnGroupMap);
            }
            PhysicalFilterItem doneFilter =
                    PhysicalQueryMapper.filter(
                            rootFieldIds.tree(),
                            rootFieldIds.idField().fieldId(),
                            "IN",
                            new ArrayList<>(recordIds));
            List<PhysicalFilterItem> combined = new ArrayList<>();
            combined.add(doneFilter);
            combined.addAll(dataPermFilters);
            return new ApprovalFilterItem(combined, false, btnGroupMap);
        }

        return new ApprovalFilterItem(dataPermFilters, false, btnGroupMap);
    }

    /** 将 (我创建的草稿)、(审批单据分支) 与 (数据权限分支) 组合为平级/嵌套的 OR 关系 */
    private List<PhysicalFilterItem> combineOrBranches(
            List<PhysicalFilterItem> draftItems,
            PhysicalFilterItem approvalBranch,
            List<PhysicalFilterItem> dataPermFilters) {
        List<PhysicalFilterItem> branches = new ArrayList<>();

        // 1. 草稿分支：created_by == me && business_status == 1
        PhysicalFilterItem draftBranch = toSingleBranchNode(draftItems);
        if (draftBranch != null) {
            branches.add(draftBranch);
        }

        // 2. 审批流单据分支：id IN (:recordIds)
        if (approvalBranch != null) {
            branches.add(approvalBranch);
        }

        // 3. 数据权限分支 (各主体数据权限条件为 AND)
        PhysicalFilterItem dataPermBranch = toSingleBranchNode(dataPermFilters);
        if (dataPermBranch != null) {
            branches.add(dataPermBranch);
        }

        if (branches.isEmpty()) {
            return Collections.emptyList();
        }
        if (branches.size() == 1) {
            return Collections.singletonList(branches.get(0));
        }

        // 递归/链式构建 OR 树：branch0 OR (branch1 OR branch2)
        PhysicalFilterItem rootOr = branches.get(branches.size() - 1);
        for (int i = branches.size() - 2; i >= 0; i--) {
            PhysicalFilterItem current = branches.get(i);
            current.setLogic("OR");
            current.setChildren(Collections.singletonList(rootOr));
            rootOr = current;
        }
        return Collections.singletonList(rootOr);
    }

    /** 将多个同级 AND 条件封装为单个独立的 Branch 节点 (若已有多个条件则首项承载自身，其余入 children) */
    private PhysicalFilterItem toSingleBranchNode(List<PhysicalFilterItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return null;
        }
        if (items.size() == 1) {
            return copyFilterItem(items.get(0));
        }
        PhysicalFilterItem head = copyFilterItem(items.get(0));
        List<PhysicalFilterItem> tail = new ArrayList<>();
        for (int i = 1; i < items.size(); i++) {
            tail.add(copyFilterItem(items.get(i)));
        }
        head.setLogic("AND");
        head.setChildren(tail);
        return head;
    }

    private PhysicalFilterItem copyFilterItem(PhysicalFilterItem src) {
        if (src == null) {
            return null;
        }
        PhysicalFilterItem copy = new PhysicalFilterItem();
        copy.setModuleId(src.getModuleId());
        copy.setTableName(src.getTableName());
        copy.setColumnName(src.getColumnName());
        copy.setOperator(src.getOperator());
        copy.setValue(src.getValue());
        copy.setLogic(src.getLogic());
        if (src.getChildren() != null) {
            List<PhysicalFilterItem> childCopies = new ArrayList<>();
            for (PhysicalFilterItem c : src.getChildren()) {
                childCopies.add(copyFilterItem(c));
            }
            copy.setChildren(childCopies);
        }
        return copy;
    }

    private List<PhysicalFilterItem> buildDraftFilterItems(
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
            List<PhysicalFilterItem> filters,
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
