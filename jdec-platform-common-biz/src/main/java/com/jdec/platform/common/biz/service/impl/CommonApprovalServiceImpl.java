package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.event.ApprovalEvent;
import com.jdec.platform.common.api.dto.request.ApprovalReq;
import com.jdec.platform.common.biz.service.CommonApprovalService;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
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
    private final DataApi dataApi;
    private final CommonApprovalFilterResolver approvalFilterResolver;
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

        if (approvalFilterResolver.isApprovalChainEnabled(rootModuleId)) {
            CommonApprovalFilterResolver.AllApprovalCountsOutcome countsOutcome =
                    approvalFilterResolver.resolveAllApprovalCountsFilters(rootModuleId, null);

            // 1. 统计【待办数量】 (approvalType = 1)
            if (!countsOutcome.todoFilterResult().shortCircuitEmpty()) {
                todoCount =
                        dataApi.countByTable(
                                TableQueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(countsOutcome.todoFilterResult().filters())
                                        .build());
            }

            // 2. 统计【已办数量】 (approvalType = 2)
            if (!countsOutcome.doneFilterResult().shortCircuitEmpty()) {
                doneCount =
                        dataApi.countByTable(
                                TableQueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(countsOutcome.doneFilterResult().filters())
                                        .build());
            }

            // 3. 统计【全部数量】 (approvalType = 3)
            if (!countsOutcome.allFilterResult().shortCircuitEmpty()) {
                totalCount =
                        dataApi.countByTable(
                                TableQueryReq.builder()
                                        .moduleId(rootModuleId)
                                        .filters(countsOutcome.allFilterResult().filters())
                                        .build());
            }
        } else {
            totalCount =
                    dataApi.countByTable(TableQueryReq.builder().moduleId(rootModuleId).build());
        }

        todo.put("count", todoCount != null ? todoCount : 0L);
        done.put("count", doneCount != null ? doneCount : 0L);
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
}
