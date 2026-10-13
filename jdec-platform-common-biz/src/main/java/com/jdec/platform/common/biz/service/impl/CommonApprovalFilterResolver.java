package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.request.PhysicalFilterItem;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 统一审批流过滤条件与按钮解析组件 (CommonApprovalFilterResolver)
 *
 * <p>职责： 保证搜索 (Search/Query)、计数 (Count) 与候选项 (Option) 遵循完全一致的审批过滤规则：
 *
 * <ul>
 *   <li>1. 待办 (approvalType = 1): (id IN :todoRecordIds) OR (created_by = :currentUserId AND
 *       business_status = 1)
 *   <li>2. 已办 (approvalType = 2): id IN :doneRecordIds
 *   <li>3. 全部 (approvalType = 3): (id IN :allApprovalRecordIds) OR (created_by = :currentUserId AND
 *       business_status = 1)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonApprovalFilterResolver {

    private final IApprovalEngine approvalEngine;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final ConfigApi configApi;

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

    /** 统一为 Search (queryByMenu) 和 Option (getOptionsByMenu) 解析指定 approvalType 的审批过滤与按钮 */
    public ApprovalFilterOutcome resolveApprovalFilters(
            Long rootModuleId, Integer approvalType, ModuleTreeDTO moduleTree) {
        if (rootModuleId == null || approvalType == null || !isApprovalChainEnabled(rootModuleId)) {
            return new ApprovalFilterOutcome(
                    new ApprovalFilterResult(Collections.emptyList(), false), null);
        }
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        ApprovalButtonsGroup approvalGroup = queryApprovalButtons(rootModuleId, approvalType);
        ApprovalFilterResult filterResult =
                buildApprovalFilters(approvalType, rootFieldIds, approvalGroup.relevantBtns());
        return new ApprovalFilterOutcome(filterResult, approvalGroup.allBtns());
    }

    /** 一次性拉取全部审批流按钮分组，供计数服务 (getApprovalCountByMenu) 分别构建待办/已办/全部统一过滤条件 */
    public AllApprovalCountsOutcome resolveAllApprovalCountsFilters(
            Long rootModuleId, ModuleTreeDTO moduleTree) {
        if (rootModuleId == null || !isApprovalChainEnabled(rootModuleId)) {
            return new AllApprovalCountsOutcome(false, null, null, null);
        }

        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        ApprovalFullButtonsGroup fullGroup = queryAllApprovalButtons(rootModuleId);

        ApprovalFilterResult todoFilterResult =
                buildApprovalFilters(1, rootFieldIds, fullGroup.todoBtns());
        ApprovalFilterResult doneFilterResult =
                buildApprovalFilters(2, rootFieldIds, fullGroup.doneBtns());
        ApprovalFilterResult allFilterResult =
                buildApprovalFilters(3, rootFieldIds, fullGroup.allBtns());

        return new AllApprovalCountsOutcome(
                true, todoFilterResult, doneFilterResult, allFilterResult);
    }

    public ApprovalButtonsGroup queryApprovalButtons(Long rootModuleId, Integer approvalType) {
        Map<Long, List<Map<String, Object>>> relevantBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> allBtns = new LinkedHashMap<>();

        if (approvalType == null) {
            return new ApprovalButtonsGroup(relevantBtns, allBtns);
        }

        try {
            List<Map<String, Object>> approvalRawList =
                    approvalEngine.getListButton(rootModuleId, approvalType);
            if (!CollectionUtils.isEmpty(approvalRawList)) {
                for (Map<String, Object> rawBtn : approvalRawList) {
                    if (rawBtn == null) {
                        continue;
                    }
                    Long recordId = parseRecordIdFromRaw(rawBtn.get("recordId"));
                    if (recordId == null) {
                        continue;
                    }

                    Object buttonKey = rawBtn.get("buttonKey");
                    Object buttonName = rawBtn.get("buttonName");
                    Map<String, Object> btnInfo = new LinkedHashMap<>();
                    btnInfo.put("buttonKey", buttonKey != null ? buttonKey.toString() : "view");
                    btnInfo.put("buttonName", buttonName != null ? buttonName.toString() : "查阅");

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

    public ApprovalFullButtonsGroup queryAllApprovalButtons(Long rootModuleId) {
        Map<Long, List<Map<String, Object>>> todoBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> doneBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> allBtns = new LinkedHashMap<>();

        try {
            List<Map<String, Object>> approvalRawList =
                    approvalEngine.getListButton(rootModuleId, 3);
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

                    String buttonKey = Objects.toString(btnInfo.get("buttonKey"), "");
                    if ("handle".equalsIgnoreCase(buttonKey)) {
                        todoBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                    } else {
                        doneBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                    }
                    allBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                }
            }
        } catch (Exception ex) {
            log.warn(
                    "获取审批链全量按钮数据失败或当前模块未配置审批流: moduleId={}, err={}", rootModuleId, ex.getMessage());
        }
        return new ApprovalFullButtonsGroup(todoBtns, doneBtns, allBtns);
    }

    public ApprovalFilterResult buildApprovalFilters(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {

        List<PhysicalFilterItem> filters = new ArrayList<>();
        if (approvalType == null) {
            return new ApprovalFilterResult(filters, false);
        }

        Long currentUserId =
                UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();

        if (approvalType == 1 || approvalType == 3) {
            // 【待办】与【全部】：(id IN recordIds) OR (created_by == currentUserId &&
            // business_status == 1)
            Set<Long> recordIds =
                    btnGroupMap != null ? btnGroupMap.keySet() : Collections.emptySet();
            boolean hasApprovalIds = !recordIds.isEmpty() && rootFieldIds.idField() != null;
            List<PhysicalFilterItem> draftItems =
                    buildDraftFilterItems(currentUserId, rootFieldIds);

            if (!hasApprovalIds && (draftItems == null || draftItems.isEmpty())) {
                return new ApprovalFilterResult(filters, true);
            }

            PhysicalFilterItem approvalBranch =
                    hasApprovalIds
                            ? PhysicalQueryMapper.filter(
                                    rootFieldIds.tree(),
                                    rootFieldIds.idField().fieldId(),
                                    "IN",
                                    new ArrayList<>(recordIds))
                            : null;

            List<PhysicalFilterItem> combined = combineBranches(approvalBranch, draftItems);
            if (combined != null && !combined.isEmpty()) {
                filters.addAll(combined);
            }
        } else if (approvalType == 2) {
            // 【已办】: id IN doneRecordIds
            Set<Long> doneRecordIds =
                    btnGroupMap != null ? btnGroupMap.keySet() : Collections.emptySet();
            if (doneRecordIds.isEmpty() || rootFieldIds.idField() == null) {
                return new ApprovalFilterResult(filters, true);
            }
            filters.add(
                    PhysicalQueryMapper.filter(
                            rootFieldIds.tree(),
                            rootFieldIds.idField().fieldId(),
                            "IN",
                            new ArrayList<>(doneRecordIds)));
        }

        return new ApprovalFilterResult(filters, false);
    }

    private List<PhysicalFilterItem> buildDraftFilterItems(
            Long currentUserId, RootModuleFieldIds rootFieldIds) {
        if (currentUserId == null
                || rootFieldIds.createdByField() == null
                || rootFieldIds.businessStatusField() == null) {
            return Collections.emptyList();
        }
        List<PhysicalFilterItem> draftItems = new ArrayList<>();
        draftItems.add(
                PhysicalQueryMapper.filter(
                        rootFieldIds.tree(),
                        rootFieldIds.createdByField().fieldId(),
                        "EQ",
                        currentUserId));
        draftItems.add(
                PhysicalQueryMapper.filter(
                        rootFieldIds.tree(),
                        rootFieldIds.businessStatusField().fieldId(),
                        "EQ",
                        1));
        return draftItems;
    }

    private List<PhysicalFilterItem> combineBranches(
            PhysicalFilterItem approvalBranch, List<PhysicalFilterItem> draftItems) {
        if (approvalBranch != null && draftItems != null && !draftItems.isEmpty()) {
            approvalBranch.setLogic("OR");
            approvalBranch.setChildren(draftItems);
            return Collections.singletonList(approvalBranch);
        }
        if (approvalBranch != null) {
            return Collections.singletonList(approvalBranch);
        }
        if (draftItems != null && !draftItems.isEmpty()) {
            return draftItems;
        }
        return Collections.emptyList();
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

    public record ApprovalFilterOutcome(
            ApprovalFilterResult filterResult, Map<Long, List<Map<String, Object>>> allBtnsMap) {}

    public record ApprovalButtonsGroup(
            Map<Long, List<Map<String, Object>>> relevantBtns,
            Map<Long, List<Map<String, Object>>> allBtns) {}

    public record ApprovalFullButtonsGroup(
            Map<Long, List<Map<String, Object>>> todoBtns,
            Map<Long, List<Map<String, Object>>> doneBtns,
            Map<Long, List<Map<String, Object>>> allBtns) {}

    public record RootModuleFieldIds(
            ModuleTreeDTO tree,
            PhysicalQueryMapper.PhysicalFieldRef idField,
            PhysicalQueryMapper.PhysicalFieldRef createdByField,
            PhysicalQueryMapper.PhysicalFieldRef businessStatusField) {}

    public record ApprovalFilterResult(
            List<PhysicalFilterItem> filters, boolean shortCircuitEmpty) {}

    public record AllApprovalCountsOutcome(
            boolean enabled,
            ApprovalFilterResult todoFilterResult,
            ApprovalFilterResult doneFilterResult,
            ApprovalFilterResult allFilterResult) {}
}
