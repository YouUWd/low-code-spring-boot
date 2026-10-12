package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.event.ApprovalEvent;
import com.jdec.platform.common.api.dto.request.ApprovalReq;
import com.jdec.platform.common.biz.service.CommonApprovalService;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.request.PhysicalFilterItem;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** 审批与流程公共聚合服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonApprovalServiceImpl implements CommonApprovalService {

    private final IApprovalEngine approvalEngine;
    private final OpenMenuApi openMenuApi;
    private final ConfigApi configApi;
    private final DataApi dataApi;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<Map<String, Object>> getApprovalCountByMenu(Long menuId) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }

        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId);

        List<Map<String, Object>> resultMap = new LinkedList<>();
        Map<String, Object> todo = new HashMap<>();
        todo.put("label", "待办");
        todo.put("type", "1");
        Map<String, Object> done = new HashMap<>();
        done.put("label", "已办");
        done.put("type", "2");
        Map<String, Object> all = new HashMap<>();
        all.put("label", "全部");
        all.put("type", "3");
        Long todoCount = 0L;
        Long doneCount = 0L;
        Long totalCount = 0L;
        if (sysApprovalChainConfigApi.getApprovalStatus(List.of(rootModuleId))) {
            // 2. 一次性获取审批链全部按钮数据 (approvalType = 3 会内部包含待办与已办)
            ApprovalButtonsGroup approvalGroup = queryAllApprovalButtons(rootModuleId);
            Map<Long, List<Map<String, Object>>> todoBtnMap = approvalGroup.todoBtns();
            Map<Long, List<Map<String, Object>>> doneBtnMap = approvalGroup.doneBtns();

            // 3. 统计【待办数量】 (approvalType = 1)
            ApprovalFilterResult todoFilterResult =
                    buildApprovalFilters(1, rootModuleId, rootFieldIds, todoBtnMap);

            if (!todoFilterResult.shortCircuitEmpty()) {
                todoCount =
                        dataApi.countByTable(
                                TableQueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(todoFilterResult.filters())
                                        .build());
            }

            // 4. 统计【已办数量】 (approvalType = 2: 直接使用审批链接口返回的数据量，无需额外查询 SQL)
            doneCount = (long) doneBtnMap.size();

            // 5. 统计【全部数量】 (结合待办分支与审批链返回的已办 id 集合)
            Set<Long> doneRecordIds = doneBtnMap.keySet();
            Set<Long> todoRecordIds = todoBtnMap.keySet();
            Set<Long> allApprovalIds = new LinkedHashSet<>(doneRecordIds);
            allApprovalIds.addAll(todoRecordIds);

            List<PhysicalFilterItem> totalFilters = new ArrayList<>();

            Long currentUserId = UserContext.getUserId();
            boolean hasApprovalIds = !allApprovalIds.isEmpty() && rootFieldIds.idField() != null;
            boolean canQueryDraft =
                    currentUserId != null
                            && rootFieldIds.createdByField() != null
                            && rootFieldIds.businessStatusField() != null;

            if (!hasApprovalIds && !canQueryDraft) {
                totalCount = 0L;
            } else {
                PhysicalFilterItem approvalBranch = null;
                if (hasApprovalIds) {
                    approvalBranch =
                            PhysicalQueryMapper.filter(
                                    rootFieldIds.tree(),
                                    rootFieldIds.idField().fieldId(),
                                    "IN",
                                    new ArrayList<>(allApprovalIds));
                }

                List<PhysicalFilterItem> draftItems = null;
                if (canQueryDraft) {
                    draftItems = new ArrayList<>();
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
                }

                if (approvalBranch != null && draftItems != null && !draftItems.isEmpty()) {
                    approvalBranch.setLogic("OR");
                    approvalBranch.setChildren(draftItems);
                    totalFilters.add(approvalBranch);
                } else if (approvalBranch != null) {
                    totalFilters.add(approvalBranch);
                } else if (draftItems != null && !draftItems.isEmpty()) {
                    totalFilters.addAll(draftItems);
                }

                totalCount =
                        dataApi.countByTable(
                                TableQueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(totalFilters)
                                        .build());
            }
        } else {
            totalCount =
                    dataApi.countByTable(TableQueryReq.builder().moduleId(rootModuleId).build());
        }
        todo.put("count", todoCount != null ? todoCount : 0L);
        done.put("count", doneCount);
        all.put("count", totalCount != null ? totalCount : 0L);
        resultMap.add(todo);
        resultMap.add(done);
        resultMap.add(all);
        return resultMap;
    }

    @Override
    public void approvalBusiness(Long menuId, ApprovalReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }

        ApprovalEvent approvalEvent = new ApprovalEvent();
        BeanUtils.copyProperties(req, approvalEvent);
        approvalEvent.setApplicantId(UserContext.getMockUserId());
        approvalEvent.setCurrentUserId(UserContext.getRealUserId());
        approvalEvent.setRoleId(UserContext.getRoleId());
        approvalEvent.setSubjectId(UserContext.getSubjectId());
        approvalEvent.setMenuId(menuId);
        approvalEvent.setModuleId(rootModuleId);
        approvalEvent.setOldRecordId(req.getRecordId());

        eventPublisher.publishEvent(approvalEvent);
    }

    @Override
    public Long BusinessNextId(Long menuId) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }

        return approvalEngine.getBusinessNextId(rootModuleId);
    }

    @Override
    public List<Map<String, Object>> getApprovalStep(Long menuId, Long id) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }

        return approvalEngine.getApprovalStep(rootModuleId, id);
    }

    private ApprovalButtonsGroup queryAllApprovalButtons(Long rootModuleId) {
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
        return new ApprovalButtonsGroup(todoBtns, doneBtns, allBtns);
    }

    private ApprovalFilterResult buildApprovalFilters(
            Integer approvalType,
            Long rootModuleId,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {

        List<PhysicalFilterItem> filters = new ArrayList<>();
        Long currentUserId = UserContext.getUserId();

        if (approvalType == 1) {
            // 【待办】: (id IN recordIds) OR (created_by == currentUserId && business_status == 1)
            Set<Long> todoRecordIds = btnGroupMap.keySet();
            boolean hasApprovalIds = !todoRecordIds.isEmpty() && rootFieldIds.idField() != null;
            boolean canQueryDraft =
                    currentUserId != null
                            && rootFieldIds.createdByField() != null
                            && rootFieldIds.businessStatusField() != null;

            if (!hasApprovalIds && !canQueryDraft) {
                return new ApprovalFilterResult(filters, true);
            }

            PhysicalFilterItem approvalBranch = null;
            if (hasApprovalIds) {
                approvalBranch =
                        PhysicalQueryMapper.filter(
                                rootFieldIds.tree(),
                                rootFieldIds.idField().fieldId(),
                                "IN",
                                new ArrayList<>(todoRecordIds));
            }

            List<PhysicalFilterItem> draftItems = null;
            if (canQueryDraft) {
                draftItems = new ArrayList<>();
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
            }

            if (approvalBranch != null && draftItems != null && !draftItems.isEmpty()) {
                approvalBranch.setLogic("OR");
                approvalBranch.setChildren(draftItems);
                filters.add(approvalBranch);
            } else if (approvalBranch != null) {
                filters.add(approvalBranch);
            } else if (draftItems != null && !draftItems.isEmpty()) {
                filters.addAll(draftItems);
            }
        }

        return new ApprovalFilterResult(filters, false);
    }

    private RootModuleFieldIds extractRootModuleFieldIds(Long rootModuleId) {
        if (rootModuleId == null) {
            return new RootModuleFieldIds(null, null, null, null);
        }

        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
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

    private record ApprovalButtonsGroup(
            Map<Long, List<Map<String, Object>>> todoBtns,
            Map<Long, List<Map<String, Object>>> doneBtns,
            Map<Long, List<Map<String, Object>>> allBtns) {}

    private record RootModuleFieldIds(
            ModuleTreeDTO tree,
            PhysicalQueryMapper.PhysicalFieldRef idField,
            PhysicalQueryMapper.PhysicalFieldRef createdByField,
            PhysicalQueryMapper.PhysicalFieldRef businessStatusField) {}

    private record ApprovalFilterResult(
            List<PhysicalFilterItem> filters, boolean shortCircuitEmpty) {}
}
