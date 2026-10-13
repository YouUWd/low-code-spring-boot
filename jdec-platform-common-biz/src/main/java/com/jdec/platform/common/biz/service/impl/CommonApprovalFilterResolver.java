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
 * <p>职责：保证搜索 (Search/Query)、计数 (Count) 与候选项 (Option) 遵循完全一致的审批过滤规则：
 *
 * <ul>
 *   <li>1. 待办 (type = 1): (id IN :todoRecordIds) OR (created_by = :currentUser AND business_status
 *       = 1)
 *   <li>2. 已办 (type = 2): id IN :doneRecordIds
 *   <li>3. 全部 (type = 3): (id IN :allRecordIds) OR (created_by = :currentUser AND business_status =
 *       1)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonApprovalFilterResolver {

    private final IApprovalEngine approvalEngine;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final ConfigApi configApi;

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

    /** 统一为列表与字段下拉解析审批过滤与按钮 (Search / Option) */
    public ApprovalFilterItem resolveApprovalFilters(
            Long rootModuleId, Integer approvalType, ModuleTreeDTO moduleTree) {
        if (rootModuleId == null || approvalType == null || !isApprovalChainEnabled(rootModuleId)) {
            return new ApprovalFilterItem(Collections.emptyList(), false, Collections.emptyMap());
        }
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        Map<Long, List<Map<String, Object>>> btnMap =
                queryApprovalButtons(rootModuleId, approvalType);
        return buildApprovalFilterItem(approvalType, rootFieldIds, btnMap);
    }

    /**
     * 一次性拉取全部审批流过滤，供计数服务 (CommonApprovalServiceImpl) 分别统计待办/已办/全部 仅调用一次
     * approvalEngine.getListButton(..., 3)，从全部按钮中自动拆分出待办 (handle) 与已办 (非 handle)
     */
    public AllApprovalCountFilters resolveAllApprovalCountsFilters(
            Long rootModuleId, ModuleTreeDTO moduleTree) {
        if (rootModuleId == null || !isApprovalChainEnabled(rootModuleId)) {
            return new AllApprovalCountFilters(false, null, null, null);
        }

        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId, moduleTree);
        Map<Long, List<Map<String, Object>>> todoBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> doneBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> allBtns = queryApprovalButtons(rootModuleId, 3);

        for (Map.Entry<Long, List<Map<String, Object>>> entry : allBtns.entrySet()) {
            Long recordId = entry.getKey();
            List<Map<String, Object>> btns = entry.getValue();
            boolean isHandle =
                    btns.stream()
                            .anyMatch(
                                    b ->
                                            "handle"
                                                    .equalsIgnoreCase(
                                                            Objects.toString(
                                                                    b.get("buttonKey"), "")));
            if (isHandle) {
                todoBtns.put(recordId, btns);
            } else {
                doneBtns.put(recordId, btns);
            }
        }

        return new AllApprovalCountFilters(
                true,
                buildApprovalFilterItem(1, rootFieldIds, todoBtns),
                buildApprovalFilterItem(2, rootFieldIds, doneBtns),
                buildApprovalFilterItem(3, rootFieldIds, allBtns));
    }

    /** 向审批引擎查询指定审批类型的记录与按钮列表 */
    public Map<Long, List<Map<String, Object>>> queryApprovalButtons(
            Long rootModuleId, Integer approvalType) {
        if (rootModuleId == null || approvalType == null) {
            return Collections.emptyMap();
        }
        try {
            List<Map<String, Object>> rawList =
                    approvalEngine.getListButton(rootModuleId, approvalType);
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

    /** 核心过滤构建：组装指定 approvalType 对应的物理表过滤项与按钮 */
    public ApprovalFilterItem buildApprovalFilterItem(
            Integer approvalType,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {

        if (approvalType == null) {
            return new ApprovalFilterItem(Collections.emptyList(), false, btnGroupMap);
        }

        Long currentUserId =
                UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
        Set<Long> recordIds = btnGroupMap != null ? btnGroupMap.keySet() : Collections.emptySet();
        boolean hasApprovalIds = !recordIds.isEmpty() && rootFieldIds.idField() != null;

        // 【待办 (1)】 与 【全部 (3)】：(id IN recordIds) OR (created_by == currentUserId && business_status
        // == 1)
        if (approvalType == 1 || approvalType == 3) {
            List<PhysicalFilterItem> draftItems =
                    buildDraftFilterItems(currentUserId, rootFieldIds);
            if (!hasApprovalIds && draftItems.isEmpty()) {
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

            return new ApprovalFilterItem(
                    combineBranches(approvalBranch, draftItems), false, btnGroupMap);
        }

        // 【已办 (2)】：id IN doneRecordIds
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
            return new ApprovalFilterItem(
                    Collections.singletonList(doneFilter), false, btnGroupMap);
        }

        return new ApprovalFilterItem(Collections.emptyList(), false, btnGroupMap);
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

    private List<PhysicalFilterItem> combineBranches(
            PhysicalFilterItem approvalBranch, List<PhysicalFilterItem> draftItems) {
        if (approvalBranch != null && !CollectionUtils.isEmpty(draftItems)) {
            approvalBranch.setLogic("OR");
            approvalBranch.setChildren(draftItems);
            return Collections.singletonList(approvalBranch);
        }
        if (approvalBranch != null) {
            return Collections.singletonList(approvalBranch);
        }
        return draftItems != null ? draftItems : Collections.emptyList();
    }

    private Map<Long, List<Map<String, Object>>> parseButtonsByRecord(
            List<Map<String, Object>> rawList) {
        if (CollectionUtils.isEmpty(rawList)) {
            return Collections.emptyMap();
        }
        Map<Long, List<Map<String, Object>>> recordBtnMap = new LinkedHashMap<>();
        for (Map<String, Object> rawBtn : rawList) {
            if (rawBtn == null) {
                continue;
            }
            Long recordId = parseRecordId(rawBtn.get("recordId"));
            if (recordId == null) {
                continue;
            }
            Map<String, Object> btnInfo = new LinkedHashMap<>(rawBtn);
            btnInfo.remove("recordId");
            btnInfo.putIfAbsent("buttonKey", "view");
            btnInfo.putIfAbsent("buttonName", "查阅");
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

    /** 审批流解析结果单项（包含过滤项集合、是否短路为空、单据按钮映射） */
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
