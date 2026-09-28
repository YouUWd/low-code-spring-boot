package com.jdec.platform.approval.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.approval.api.AsyncTaskService;
import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.dto.ApprovalProcessDTO;
import com.jdec.platform.approval.api.event.ApprovalEvent;
import com.jdec.platform.approval.api.req.*;
import com.jdec.platform.approval.api.resp.ActionLogResp;
import com.jdec.platform.approval.biz.entity.*;
import com.jdec.platform.approval.biz.mapper.*;
import com.jdec.platform.config.api.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.SecurityUtils;
import com.jdec.platform.shared.security.model.LoginUser;
import com.jdec.platform.shared.service.ConfigChangeMessagePublisher;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngineImpl implements IApprovalEngine {

    private final ApprovalChainMapper approvalChainMapper;
    private final ApprovalBusinessMapper approvalBusinessMapper;
    private final ApprovalChainButtonMapper approvalChainButtonMapper;
    private final ApprovalChainStatusMapper approvalChainStatusMapper;
    private final ApprovalVersionRecordMapper approvalVersionRecordMapper;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final SysUserApi sysUserApi;
    private final SysStatusApi sysStatusApi;
    private final SysConfigCategoryApi sysConfigCategoryApi;
    private final SysButtonApi sysButtonApi;
    private final SysRolePermissionApi sysRolePermissionApi;
    private final ConfigChangeMessagePublisher messagePublisher;
    private final AsyncTaskService asyncTaskService; // 注入异步 Service

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleApprovalEvent(ApprovalEvent event) {
        log.info("收到模块配置变更事件: eventType={}, moduleId={}", event.getButton(), event.getModuleId());
        ApprovalProcessDTO approvalProcessDTO = convertApprovalDTO(event);
        if (List.of("submit", "edit", "change").contains(approvalProcessDTO.getButton())) {
            businessSubmit(approvalProcessDTO);
        } else if ("create".equals(approvalProcessDTO.getButton())) {
            businessCreate(approvalProcessDTO);
        } else if ("pass".equals(approvalProcessDTO.getButton())) {
            businessApprove(approvalProcessDTO);
        } else if ("reject".equals(approvalProcessDTO.getButton())) {
            businessReject(approvalProcessDTO);
        } else if ("cancel".equals(approvalProcessDTO.getButton())) {
            businessRevoke(approvalProcessDTO);
        } else {
            throw new BusinessException(603, "审批操作异常");
        }
    }

    // 查询单据审批详细数据--------------------------------------------------------------------
    @Override
    public Map<String, Object> getApprovalDetail(ApprovalProcessReq req) {
        if (Objects.isNull(req.getRoleId())) {
            throw new BusinessException(601, "角色为空");
        }
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<ApprovalVersionRecord> businessWrapper = new LambdaQueryWrapper<>();
        businessWrapper.eq(ApprovalVersionRecord::getModuleId, req.getModuleId());
        businessWrapper
                .eq(ApprovalVersionRecord::getRecordId, req.getRecordId())
                .orderByDesc(ApprovalVersionRecord::getId)
                .last("LIMIT 1");
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(businessWrapper);
        if (Objects.isNull(approvalVersionRecord)) {
            throw new BusinessException(601, "单据记录不存在");
        }
        // 单据编号
        result.put("businessNumber", approvalVersionRecord.getBusinessNumber());
        // 创建日期
        result.put("createdDate", approvalVersionRecord.getCreatedDate());
        // 版本号列表
        Object[] versionArr =
                getVersionList(approvalVersionRecord.getBusinessNumber(), req.getButton())
                        .toArray();
        List<Map<String, Object>> versionList = (List<Map<String, Object>>) versionArr[0];
        result.put("versionList", versionList);
        // 当前状态
        Map<String, Object> approvalStatus = new HashMap<>();
        if (Objects.nonNull(req.getButton())
                && (req.getButton().equals("change") || req.getButton().equals("change-change"))) {
            ModuleApprovalChainConfigResp.StatusNode statusNode =
                    sysStatusApi.getStatusByValue(1, req.getModuleId());
            if (Objects.isNull(statusNode)) {
                throw new BusinessException(601, "单据记录异常");
            }
            approvalStatus.put("id", statusNode.getId());
            approvalStatus.put("statusValue", statusNode.getStatusValue());
            approvalStatus.put("title", statusNode.getTitle());
            approvalStatus.put("statusBackground", statusNode.getStatusBackground());
            approvalStatus.put("statusFontColor", statusNode.getStatusFontColor());
            // 当前版本号
            result.put("versionNo", versionList.getFirst().get("versionNo"));
            // 是否显示对比
            result.put("showChangeList", false);
        } else {
            approvalStatus.put("id", approvalVersionRecord.getStatusId());
            approvalStatus.put("statusValue", approvalVersionRecord.getStatusValue());
            approvalStatus.put("title", approvalVersionRecord.getStatusTitle());
            approvalStatus.put("statusBackground", approvalVersionRecord.getStatusBackground());
            approvalStatus.put("statusFontColor", approvalVersionRecord.getStatusFontColor());
            // 当前版本号
            result.put("versionNo", approvalVersionRecord.getVersionNo());
        }
        result.put("approvalStatus", approvalStatus);
        if (approvalVersionRecord.getCurrentStep() > 0
                && (Objects.isNull(req.getButton())
                        || (!req.getButton().equals("change")
                                && !req.getButton().equals("change-change")))) {
            // 审批链步骤
            List<Map<String, Object>> step =
                    approvalChainStep(
                            approvalVersionRecord.getSerialNumber(),
                            approvalVersionRecord.getSerialNo(),
                            req.getRoleId(),
                            req.getApplicantId());
            result.put("step", step);
        } else {
            result.put("step", new ArrayList<>());
        }
        // 创建人
        result.put("createUser", convertUserResp(approvalVersionRecord.getCreatedBy()));

        // 操作按钮、是否可编辑
        AtomicReference<Integer> canEdit = new AtomicReference<>();
        canEdit.set(0);
        List<Map<String, Object>> operationButton = getOperationButton(req, canEdit);
        result.put("canEdit", canEdit.get());
        result.put("btnList", operationButton);
        return result;
    }

    // 查询操作人在当前模块下的审批链业务代办/已办/全部数量-------------------------------------------
    @Override
    public List<Map<String, Object>> getBusinessTypeList(ApprovalListReq approvalProcessReq) {
        List<Map<String, Object>> resultMap = new LinkedList<>();
        if (approvalProcessReq.getModuleIdList().isEmpty()) {
            //   || approvalProcessReq.getSubjectIdList().isEmpty()) {
            return resultMap;
        }
        List<Long> approvalTypeIds =
                sysApprovalChainConfigApi.getApprovalTypeIdList(
                        approvalProcessReq.getModuleIdList(), approvalProcessReq.getRoleId());
        LambdaQueryWrapper<ApprovalBusiness> todoWrapper = new LambdaQueryWrapper<>();
        todoWrapper.in(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleIdList());
        //   todoWrapper.in(ApprovalBusiness::getSubjectId, approvalProcessReq.getSubjectIdList());
        todoWrapper.and(
                wrapper ->
                        wrapper.nested(
                                        wp1 ->
                                                wp1.eq(
                                                                ApprovalBusiness::getApproverId,
                                                                approvalProcessReq.getApplicantId())
                                                        .eq(
                                                                ApprovalBusiness::getApproveRoleId,
                                                                approvalProcessReq.getRoleId())
                                                        .ne(ApprovalBusiness::getCurrentStep, 0))
                                .or()
                                .nested(
                                        wp2 ->
                                                wp2.isNull(ApprovalBusiness::getApproverId)
                                                        .eq(
                                                                ApprovalBusiness::getApproveRoleId,
                                                                approvalProcessReq.getRoleId())
                                                        .ne(ApprovalBusiness::getCurrentStep, 0)
                                                        .apply(
                                                                "NOT FIND_IN_SET({0}, IFNULL(approver_id_list, ''))",
                                                                approvalProcessReq
                                                                        .getApplicantId()))
                                .or()
                                .nested(
                                        wp3 ->
                                                wp3.isNull(ApprovalBusiness::getApproverId)
                                                        .eq(
                                                                ApprovalBusiness::getCreatedBy,
                                                                approvalProcessReq.getApplicantId())
                                                        .eq(ApprovalBusiness::getCurrentStep, 0)));
        todoWrapper.notIn(ApprovalBusiness::getStatusValue, 99, -99);
        List<ApprovalBusiness> todoList = approvalBusinessMapper.selectList(todoWrapper);
        List<ApprovalBusiness> submitList =
                todoList.stream().filter(item -> item.getCurrentStep() == 0).toList();
        for (ApprovalBusiness item : submitList) {
            ApprovalProcessDTO approvalProcessDTO = new ApprovalProcessDTO();
            approvalProcessDTO.setModuleId(item.getModuleId());
            approvalProcessDTO.setRoleId(approvalProcessReq.getRoleId());
            if (!checkRight(approvalProcessDTO)) {
                todoList.remove(item);
            }
        }
        todoList.removeIf(
                item ->
                        !Objects.equals(item.getCreatedBy(), approvalProcessReq.getApplicantId())
                                && !approvalTypeIds.contains(item.getApprovalChainTypeId()));
        Map<String, Object> todo = new HashMap<>();
        todo.put("label", "待办");
        todo.put("type", "1");
        todo.put("count", todoList.size());
        resultMap.add(todo);
        LambdaQueryWrapper<ApprovalBusiness> processWrapper = new LambdaQueryWrapper<>();
        processWrapper.in(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleIdList());
        // processWrapper.in(ApprovalBusiness::getSubjectId, approvalProcessReq.getSubjectIdList());
        // processWrapper.eq(ApprovalBusiness::getApprovalUids,
        // approvalProcessReq.getApplicantId());
        processWrapper.apply(
                "FIND_IN_SET({0}, approval_uids)", approvalProcessReq.getApplicantId());
        List<ApprovalBusiness> processList = approvalBusinessMapper.selectList(processWrapper);
        processList.removeIf(
                item ->
                        !Objects.equals(item.getCreatedBy(), approvalProcessReq.getApplicantId())
                                && !approvalTypeIds.contains(item.getApprovalChainTypeId()));
        Map<String, Object> done = new HashMap<>();
        done.put("label", "已办");
        done.put("type", "2");
        done.put("count", processList.size());
        resultMap.add(done);
        LambdaQueryWrapper<ApprovalBusiness> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.in(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleIdList());
        // allWrapper.in(ApprovalBusiness::getSubjectId, approvalProcessReq.getSubjectIdList());
        if (Objects.nonNull(approvalProcessReq.getRecordIdList())
                && !approvalProcessReq.getRecordIdList().isEmpty()) {
            allWrapper.in(ApprovalBusiness::getRecordId, approvalProcessReq.getRecordIdList());
        }
        if (Objects.nonNull(approvalProcessReq.getAllowedIdList())
                && !approvalProcessReq.getAllowedIdList().isEmpty()) {
            allWrapper.notIn(ApprovalBusiness::getRecordId, approvalProcessReq.getAllowedIdList());
        }
        List<ApprovalBusiness> allList = approvalBusinessMapper.selectList(allWrapper);
        allList.removeIf(
                item ->
                        !Objects.equals(item.getCreatedBy(), approvalProcessReq.getApplicantId())
                                && !approvalTypeIds.contains(item.getApprovalChainTypeId()));
        for (ApprovalBusiness item : submitList) {
            ApprovalProcessDTO approvalProcessDTO = new ApprovalProcessDTO();
            approvalProcessDTO.setModuleId(item.getModuleId());
            approvalProcessDTO.setRoleId(approvalProcessReq.getRoleId());
            if (!checkRight(approvalProcessDTO)) {
                allList.removeIf(iterate -> iterate.getId().equals(item.getId()));
            }
        }
        if (Objects.nonNull(approvalProcessReq.getAllowedIdList())
                && !approvalProcessReq.getAllowedIdList().isEmpty()) {
            LambdaQueryWrapper<ApprovalBusiness> allowedWrapper = new LambdaQueryWrapper<>();
            allowedWrapper.in(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleIdList());
            // allowedWrapper.in(
            //         ApprovalBusiness::getSubjectId, approvalProcessReq.getSubjectIdList());
            allowedWrapper.in(ApprovalBusiness::getRecordId, approvalProcessReq.getAllowedIdList());
            List<ApprovalBusiness> allowedList = approvalBusinessMapper.selectList(allowedWrapper);
            allList.addAll(allowedList);
        }
        Map<String, Object> all = new HashMap<>();
        all.put("label", "全部");
        all.put("type", "3");
        all.put("count", allList.size());
        resultMap.add(all);
        return resultMap;
    }

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部列表------------------------------------------------
    @Override
    public List<Map<String, Object>> getBusinessList(ApprovalListReq approvalListReq) {
        List<ApprovalBusiness> toDoList = new ArrayList<>();
        List<Map<String, Object>> respList = new ArrayList<>();
        if (approvalListReq.getModuleIdList().isEmpty()) {
            //   || approvalListReq.getSubjectIdList().isEmpty()) {
            return respList;
        }
        List<Long> approvalTypeIds =
                sysApprovalChainConfigApi.getApprovalTypeIdList(
                        approvalListReq.getModuleIdList(), approvalListReq.getRoleId());
        if (approvalListReq.getApprovalType() != 2) {
            LambdaQueryWrapper<ApprovalBusiness> todoWrapper = new LambdaQueryWrapper<>();
            todoWrapper.in(ApprovalBusiness::getModuleId, approvalListReq.getModuleIdList());
            //  todoWrapper.in(ApprovalBusiness::getSubjectId, approvalListReq.getSubjectIdList());
            todoWrapper.and(
                    wrapper ->
                            wrapper.nested(
                                            wp1 ->
                                                    wp1.eq(
                                                                    ApprovalBusiness::getApproverId,
                                                                    approvalListReq
                                                                            .getApplicantId())
                                                            .eq(
                                                                    ApprovalBusiness
                                                                            ::getApproveRoleId,
                                                                    approvalListReq.getRoleId())
                                                            .ne(
                                                                    ApprovalBusiness
                                                                            ::getCurrentStep,
                                                                    0))
                                    .or()
                                    .nested(
                                            wp2 ->
                                                    wp2.isNull(ApprovalBusiness::getApproverId)
                                                            .eq(
                                                                    ApprovalBusiness
                                                                            ::getApproveRoleId,
                                                                    approvalListReq.getRoleId())
                                                            .ne(ApprovalBusiness::getCurrentStep, 0)
                                                            .apply(
                                                                    "NOT FIND_IN_SET({0}, IFNULL(approver_id_list, ''))",
                                                                    approvalListReq
                                                                            .getApplicantId()))
                                    .or()
                                    .nested(
                                            wp3 ->
                                                    wp3.isNull(ApprovalBusiness::getApproverId)
                                                            .eq(
                                                                    ApprovalBusiness::getCreatedBy,
                                                                    approvalListReq
                                                                            .getApplicantId())
                                                            .eq(
                                                                    ApprovalBusiness
                                                                            ::getCurrentStep,
                                                                    0)));
            todoWrapper.notIn(ApprovalBusiness::getStatusValue, 99, -99);
            // todoWrapper.in(ApprovalBusiness::getRecordId, approvalListReq.getRecordIdList());
            toDoList = approvalBusinessMapper.selectList(todoWrapper);
            List<ApprovalBusiness> submitList =
                    toDoList.stream().filter(item -> item.getCurrentStep() == 0).toList();
            for (ApprovalBusiness item : submitList) {
                ApprovalProcessDTO approvalProcessDTO = new ApprovalProcessDTO();
                approvalProcessDTO.setModuleId(item.getModuleId());
                approvalProcessDTO.setRoleId(approvalListReq.getRoleId());
                if (!checkRight(approvalProcessDTO)) {
                    toDoList.remove(item);
                }
            }
            toDoList.removeIf(
                    item ->
                            !Objects.equals(item.getCreatedBy(), approvalListReq.getApplicantId())
                                    && !approvalTypeIds.contains(item.getApprovalChainTypeId()));
            if (approvalListReq.getApprovalType() == 1) {
                respList =
                        toDoList.stream()
                                .map(business -> convertMapResp(business, 1))
                                .collect(Collectors.toList());
            }
        } else {
            LambdaQueryWrapper<ApprovalBusiness> processWrapper = new LambdaQueryWrapper<>();
            processWrapper.in(ApprovalBusiness::getModuleId, approvalListReq.getModuleIdList());
            //  processWrapper.in(ApprovalBusiness::getSubjectId,
            // approvalListReq.getSubjectIdList());
            processWrapper.apply(
                    "FIND_IN_SET({0}, approval_uids)", approvalListReq.getApplicantId());
            //            if (Objects.nonNull(approvalListReq.getRecordIdList())
            //                    && !approvalListReq.getRecordIdList().isEmpty()) {
            //                processWrapper.in(ApprovalBusiness::getRecordId,
            // approvalListReq.getRecordIdList());
            //            }
            List<ApprovalBusiness> resultList = approvalBusinessMapper.selectList(processWrapper);
            resultList.removeIf(
                    item ->
                            !Objects.equals(item.getCreatedBy(), approvalListReq.getApplicantId())
                                    && !approvalTypeIds.contains(item.getApprovalChainTypeId()));
            respList =
                    resultList.stream()
                            .map(business -> convertMapResp(business, 2))
                            .collect(Collectors.toList());
        }
        if (approvalListReq.getApprovalType() == 3) {
            LambdaQueryWrapper<ApprovalBusiness> allWrapper = new LambdaQueryWrapper<>();
            allWrapper.in(ApprovalBusiness::getModuleId, approvalListReq.getModuleIdList());
            //   allWrapper.in(ApprovalBusiness::getSubjectId, approvalListReq.getSubjectIdList());
            //            if (Objects.nonNull(approvalListReq.getRecordIdList())
            //                    && !approvalListReq.getRecordIdList().isEmpty()) {
            //                allWrapper.in(ApprovalBusiness::getRecordId,
            // approvalListReq.getRecordIdList());
            //            }
            //            if (Objects.nonNull(approvalListReq.getAllowedIdList())
            //                    && !approvalListReq.getAllowedIdList().isEmpty()) {
            //                allWrapper.notIn(ApprovalBusiness::getRecordId,
            // approvalListReq.getAllowedIdList());
            //            }
            List<ApprovalBusiness> allList = approvalBusinessMapper.selectList(allWrapper);
            allList.removeIf(
                    item ->
                            !Objects.equals(item.getCreatedBy(), approvalListReq.getApplicantId())
                                    && !approvalTypeIds.contains(item.getApprovalChainTypeId()));
            Set<Long> ids =
                    toDoList.stream().map(ApprovalBusiness::getId).collect(Collectors.toSet());
            allList.removeIf(
                    item ->
                            Objects.equals(item.getCreatedBy(), approvalListReq.getApplicantId())
                                    && item.getCurrentStep().equals(0)
                                    && !ids.contains(item.getId()));
            if (Objects.nonNull(approvalListReq.getAllowedIdList())
                    && !approvalListReq.getAllowedIdList().isEmpty()) {
                LambdaQueryWrapper<ApprovalBusiness> allowedWrapper = new LambdaQueryWrapper<>();
                allowedWrapper.in(ApprovalBusiness::getModuleId, approvalListReq.getModuleIdList());
                //   allowedWrapper.in(
                //           ApprovalBusiness::getSubjectId, approvalListReq.getSubjectIdList());
                //                allowedWrapper.in(
                //                        ApprovalBusiness::getRecordId,
                // approvalListReq.getAllowedIdList());
                List<ApprovalBusiness> allowedList =
                        approvalBusinessMapper.selectList(allowedWrapper);
                allList.addAll(allowedList);
            }
            for (ApprovalBusiness item : allList) {
                if (ids.contains(item.getId())) {
                    respList.add(convertMapResp(item, 1));
                } else {
                    respList.add(convertMapResp(item, 3));
                }
            }
        }
        return respList;
    }

    @Override
    public List<Map<String, Object>> getListNumber(Long moduleId) {
        if (!sysApprovalChainConfigApi.getApprovalStatus(List.of(moduleId))) {
            return null;
        }
        LoginUser loginUserInfo = SecurityUtils.getLoginUser();
        boolean isMockLogin =
                loginUserInfo != null && Boolean.TRUE.equals(loginUserInfo.getIsMockLogin());
        Long mockUserId = isMockLogin ? loginUserInfo.getMockUserId() : loginUserInfo.getUserId();
        ApprovalListReq req = new ApprovalListReq();
        req.setModuleIdList(List.of(moduleId));
        req.setRoleId(loginUserInfo.getRoleId());
        req.setApplicantId(mockUserId);
        return getBusinessTypeList(req);
    }

    @Override
    public List<Map<String, Object>> getListButton(Long moduleId, Integer approvalType) {
        LoginUser loginUserInfo = SecurityUtils.getLoginUser();
        boolean isMockLogin =
                loginUserInfo != null && Boolean.TRUE.equals(loginUserInfo.getIsMockLogin());
        Long mockUserId = isMockLogin ? loginUserInfo.getMockUserId() : loginUserInfo.getUserId();
        ApprovalListReq req = new ApprovalListReq();
        req.setApprovalType(approvalType);
        req.setModuleIdList(List.of(moduleId));
        req.setRoleId(loginUserInfo.getRoleId());
        req.setApplicantId(mockUserId);
        return getBusinessList(req);
    }

    @Override
    public Map<String, Object> getApprovalData(Long moduleId, Long recordId, String button) {
        LoginUser loginUserInfo = SecurityUtils.getLoginUser();
        boolean isMockLogin =
                loginUserInfo != null && Boolean.TRUE.equals(loginUserInfo.getIsMockLogin());
        Long mockUserId = isMockLogin ? loginUserInfo.getMockUserId() : loginUserInfo.getUserId();
        ApprovalProcessReq req = new ApprovalProcessReq();
        req.setModuleId(moduleId);
        req.setRecordId(recordId);
        req.setRoleId(loginUserInfo.getRoleId());
        req.setApplicantId(mockUserId);
        req.setButton(button);
        return getApprovalDetail(req);
    }

    // 根据用户ID和角色ID查询用户指定业务的下一单ID------------------------------------------------
    @Override
    public Long getBusinessNextId(ApprovalProcessReq approvalProcessReq) {
        ApprovalProcessDTO approvalProcessDTO = convertApprovalDTO(approvalProcessReq);
        LambdaQueryWrapper<ApprovalBusiness> todoWrapper = new LambdaQueryWrapper<>();
        todoWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleId());
        todoWrapper.eq(ApprovalBusiness::getSubjectId, AppContext.getSubjectId());
        todoWrapper.notIn(ApprovalBusiness::getStatusValue, 99, -99);
        todoWrapper.ne(ApprovalBusiness::getCurrentStep, 0);
        todoWrapper.and(
                wrapper ->
                        wrapper.nested(
                                        wp1 ->
                                                wp1.eq(
                                                                ApprovalBusiness::getApproverId,
                                                                approvalProcessReq.getApplicantId())
                                                        .eq(
                                                                ApprovalBusiness::getApproveRoleId,
                                                                approvalProcessReq.getRoleId()))
                                .or()
                                .nested(
                                        wp2 ->
                                                wp2.isNull(ApprovalBusiness::getApproverId)
                                                        .eq(
                                                                ApprovalBusiness::getApproveRoleId,
                                                                approvalProcessReq.getRoleId())
                                                        .apply(
                                                                "NOT FIND_IN_SET({0}, IFNULL(approver_id_list, ''))",
                                                                approvalProcessReq
                                                                        .getApplicantId())));
        List<ApprovalBusiness> todoList = approvalBusinessMapper.selectList(todoWrapper);
        List<ApprovalBusiness> submitList = new ArrayList<>();
        if (checkRight(approvalProcessDTO)) {
            todoWrapper = new LambdaQueryWrapper<>();
            todoWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleId());
            todoWrapper.eq(ApprovalBusiness::getSubjectId, AppContext.getSubjectId());
            todoWrapper.notIn(ApprovalBusiness::getStatusValue, 99, -99);
            todoWrapper.isNull(ApprovalBusiness::getApproverId);
            todoWrapper.eq(ApprovalBusiness::getCreatedBy, approvalProcessReq.getApplicantId());
            todoWrapper.eq(ApprovalBusiness::getCurrentStep, 0);
            submitList = approvalBusinessMapper.selectList(todoWrapper);
        }
        todoList.addAll(submitList);
        return !todoList.isEmpty() ? todoList.getFirst().getRecordId() : 0;
    }

    @Override
    public List<Map<String, Object>> getApprovalStep(ApprovalDataReq req) {
        List<Map<String, Object>> result = new ArrayList<>();
        // 查询当前单据记录
        LambdaQueryWrapper<ApprovalVersionRecord> businessWrapper = new LambdaQueryWrapper<>();
        businessWrapper.eq(ApprovalVersionRecord::getModuleId, req.getModuleId());
        businessWrapper
                .eq(ApprovalVersionRecord::getRecordId, req.getRecordId())
                .orderByDesc(ApprovalVersionRecord::getId)
                .last("LIMIT 1");
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(businessWrapper);
        // 查询步骤列表 + 关联状态
        LambdaQueryWrapper<ApprovalChain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalChain::getSerialNumber, approvalVersionRecord.getSerialNumber())
                .eq(ApprovalChain::getSerialNo, approvalVersionRecord.getSerialNo())
                .eq(ApprovalChain::getApproved, true)
                .orderByAsc(ApprovalChain::getCurrentStep);
        List<ApprovalChain> approvalChainlist = approvalChainMapper.selectList(wrapper);
        // 5. 遍历步骤
        for (ApprovalChain item : approvalChainlist) {
            LambdaQueryWrapper<ApprovalChainStatus> approvalChainStatusWrapper =
                    new LambdaQueryWrapper<>();
            approvalChainStatusWrapper.eq(
                    ApprovalChainStatus::getSerialNumber, item.getSerialNumber());
            approvalChainStatusWrapper.eq(ApprovalChainStatus::getSerialNo, item.getSerialNo());
            approvalChainStatusWrapper
                    .eq(ApprovalChainStatus::getPid, item.getCurrentStatusChildId())
                    .last("limit 1");
            ApprovalChainStatus status =
                    approvalChainStatusMapper.selectOne(approvalChainStatusWrapper);
            // 组装步骤数据
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getCurrentStatusChildId());
            map.put("title", status == null ? "" : status.getTitle());
            map.put("statusBackground", status == null ? "" : status.getStatusBackground());
            map.put("statusFontColor", status == null ? "" : status.getStatusFontColor());
            map.put("statusValue", status == null ? "" : status.getStatusValue());
            map.put("step", item.getCurrentStep());
            result.add(map);
        }
        return result;
    }

    // 新建业务发起审批申请------------------------------------------------------------------------
    @Transactional
    public void businessCreate(ApprovalProcessDTO approvalProcessDTO) {
        String businessNumber = approvalProcessDTO.getBusinessNumber();
        String serialNumber = businessNumber + "@1";
        // 初始化审批链
        approvalProcessDTO.setSerialNumber(serialNumber);
        approvalProcessDTO.setSerialNo(1);
        initApprovalChainForModule(approvalProcessDTO);

        // 查询起始审批节点
        LambdaQueryWrapper<ApprovalChain> sysApprovalChainWrapper = new LambdaQueryWrapper<>();
        sysApprovalChainWrapper.eq(
                ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
        sysApprovalChainWrapper.eq(ApprovalChain::getCurrentStep, 1).last("LIMIT 1");
        ApprovalChain sysApprovalChain = approvalChainMapper.selectOne(sysApprovalChainWrapper);
        if (Objects.isNull(sysApprovalChain)) {
            throw new BusinessException(601, "单据记录异常");
        }
        LambdaQueryWrapper<ApprovalChainStatus> approvalChainStatusWrapper =
                new LambdaQueryWrapper<>();
        approvalChainStatusWrapper.eq(
                ApprovalChainStatus::getSerialNumber, sysApprovalChain.getSerialNumber());
        approvalChainStatusWrapper.eq(
                ApprovalChainStatus::getSerialNo, sysApprovalChain.getSerialNo());
        approvalChainStatusWrapper
                .eq(ApprovalChainStatus::getPid, sysApprovalChain.getCurrentStatusChildId())
                .last("limit 1");
        ApprovalChainStatus approvalChainStatus =
                approvalChainStatusMapper.selectOne(approvalChainStatusWrapper);
        if (Objects.isNull(approvalChainStatus)) {
            throw new BusinessException(601, "单据记录异常");
        }
        // 创建业务单据记录
        ApprovalBusiness approvalBusiness = new ApprovalBusiness();
        approvalBusiness.setSubjectId(approvalProcessDTO.getSubjectId());
        approvalBusiness.setModuleId(approvalProcessDTO.getModuleId());
        approvalBusiness.setRecordId(approvalProcessDTO.getRecordId());
        approvalBusiness.setBusinessNumber(businessNumber);
        approvalBusiness.setVersionNo(1);
        approvalBusiness.setSerialNumber(serialNumber);
        approvalBusiness.setSerialNo(1);
        approvalBusiness.setCurrentStep(1);
        approvalBusiness.setStatusId(sysApprovalChain.getCurrentStatusChildId());
        approvalBusiness.setStatusTitle(approvalChainStatus.getTitle());
        approvalBusiness.setStatusBackground(approvalChainStatus.getStatusBackground());
        approvalBusiness.setStatusFontColor(approvalChainStatus.getStatusFontColor());
        approvalBusiness.setStatusValue(approvalChainStatus.getStatusValue());
        approvalBusiness.setApprovalChainTypeId(sysApprovalChain.getApprovalChainTypeId());
        approvalBusiness.setApproverId(sysApprovalChain.getApproverId());
        approvalBusiness.setApproveRoleId(sysApprovalChain.getApproveRoleId());
        approvalBusiness.setApprovalUids(approvalProcessDTO.getApplicantId().toString());
        approvalBusiness.setCreatedBy(approvalProcessDTO.getApplicantId());
        approvalBusiness.setCreatedDate(LocalDateTime.now());
        approvalBusiness.setUpdatedBy(approvalProcessDTO.getApplicantId());
        approvalBusiness.setUpdatedDate(LocalDateTime.now());
        approvalBusiness.setDeleted(false);
        approvalBusinessMapper.insert(approvalBusiness);

        // 创建业务单据详细内容记录
        ApprovalVersionRecord approvalVersionRecord = new ApprovalVersionRecord();
        approvalVersionRecord.setSubjectId(approvalProcessDTO.getSubjectId());
        approvalVersionRecord.setModuleId(approvalProcessDTO.getModuleId());
        approvalVersionRecord.setRecordId(approvalProcessDTO.getRecordId());
        approvalVersionRecord.setBusinessNumber(businessNumber);
        approvalVersionRecord.setVersionNo(1);
        approvalVersionRecord.setSerialNumber(serialNumber);
        approvalVersionRecord.setSerialNo(1);
        approvalVersionRecord.setCurrentStep(1);
        approvalVersionRecord.setStatusId(sysApprovalChain.getCurrentStatusChildId());
        approvalVersionRecord.setStatusTitle(approvalChainStatus.getTitle());
        approvalVersionRecord.setStatusBackground(approvalChainStatus.getStatusBackground());
        approvalVersionRecord.setStatusFontColor(approvalChainStatus.getStatusFontColor());
        approvalVersionRecord.setStatusValue(approvalChainStatus.getStatusValue());
        approvalVersionRecord.setSourceId(0L);
        approvalVersionRecord.setApprovalChainTypeId(sysApprovalChain.getApprovalChainTypeId());
        approvalVersionRecord.setCreatedBy(approvalProcessDTO.getApplicantId());
        approvalVersionRecord.setCreatedDate(LocalDateTime.now());
        approvalVersionRecord.setUpdatedBy(approvalProcessDTO.getApplicantId());
        approvalVersionRecord.setUpdatedDate(LocalDateTime.now());
        approvalVersionRecordMapper.insert(approvalVersionRecord);

        // 发送websocket消息
        Map<String, Object> payload = new HashMap<>();
        payload.put("businessNumber", approvalBusiness.getBusinessNumber());
        payload.put("moduleId", approvalBusiness.getModuleId());
        payload.put("recordId", approvalBusiness.getRecordId());
        payload.put("applicantId", approvalProcessDTO.getApplicantId());
        messagePublisher.publish(
                approvalBusiness.getSubjectId(),
                AppContext.getProjectNo(),
                ChangeType.APPROVAL_UPDATE,
                payload);
        approvalProcessDTO.setCurrentStep(approvalBusiness.getCurrentStep());
        approvalProcessDTO.setCreatedBy(approvalBusiness.getCreatedBy());
        approvalProcessDTO.setApprovalUids(approvalBusiness.getApprovalUids());
        // 写入日志
        asyncTaskService.saveAuditLogAsync(AppContext.getProjectNo(), approvalProcessDTO);
        // 发送消息
        asyncTaskService.sendWechatMessageAsync(
                AppContext.getProjectNo(), approvalProcessDTO, 1, "submit", 0L);
    }

    // 变更业务发起审批申请------------------------------------------------------------------------
    @Transactional
    public void businessSubmit(ApprovalProcessDTO approvalProcessDTO) {
        // 查询业务单据记录
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper
                .eq(ApprovalBusiness::getRecordId, approvalProcessDTO.getOldRecordId())
                .last("limit 1");
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);
        if (Objects.isNull(approvalBusiness)) {
            throw new BusinessException(601, "单据记录异常");
        }
        if (!approvalBusiness.getCreatedBy().equals(approvalProcessDTO.getApplicantId())) {
            throw new BusinessException(602, "申请人与操作人不一致");
        }
        LambdaQueryWrapper<ApprovalVersionRecord> approvalVersionWrapper =
                new LambdaQueryWrapper<>();
        approvalVersionWrapper.eq(
                ApprovalVersionRecord::getBusinessNumber, approvalBusiness.getBusinessNumber());
        approvalVersionWrapper
                .orderByDesc(ApprovalVersionRecord::getId)
                .last("limit 1"); // 追加SQL limit
        ApprovalVersionRecord approvalRecord =
                approvalVersionRecordMapper.selectOne(approvalVersionWrapper);
        if (Objects.isNull(approvalRecord)) {
            throw new BusinessException(601, "单据记录异常");
        }

        // 生成版本和编号
        int versionNo = approvalRecord.getVersionNo();
        Long sourceId = approvalRecord.getId();
        if (approvalRecord.getCurrentStep().equals(0) && approvalRecord.getStatusValue() != -99) {
            // 驳回提交
            approvalProcessDTO.setSerialNumber(approvalRecord.getSerialNumber());
            approvalProcessDTO.setSerialNo(approvalRecord.getSerialNo() + 1);
            sourceId = approvalRecord.getSourceId();
        } else if (approvalRecord.getStatusValue() == 99) {
            // 直接提交
            versionNo = approvalRecord.getVersionNo() + 1;
            String serialNumber = approvalRecord.getBusinessNumber() + "@" + versionNo;
            approvalProcessDTO.setSerialNumber(serialNumber);
            approvalProcessDTO.setSerialNo(1);
        } else if (approvalRecord.getStatusValue() == -99) {
            // 直接提交
            versionNo = approvalRecord.getVersionNo() + 1;
            String serialNumber = approvalRecord.getBusinessNumber() + "@" + versionNo;
            approvalProcessDTO.setSerialNumber(serialNumber);
            approvalProcessDTO.setSerialNo(1);
            sourceId = approvalRecord.getSourceId();
        }

        // 初始化审批链
        initApprovalChainForModule(approvalProcessDTO);

        // 查询起始审批节点
        LambdaQueryWrapper<ApprovalChain> sysApprovalChainWrapper = new LambdaQueryWrapper<>();
        sysApprovalChainWrapper.eq(
                ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
        sysApprovalChainWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
        sysApprovalChainWrapper.eq(ApprovalChain::getCurrentStep, 1).last("LIMIT 1");
        ApprovalChain sysApprovalChain = approvalChainMapper.selectOne(sysApprovalChainWrapper);
        if (Objects.isNull(sysApprovalChain)) {
            throw new BusinessException(601, "单据记录异常");
        }
        LambdaQueryWrapper<ApprovalChainStatus> approvalChainStatusWrapper =
                new LambdaQueryWrapper<>();
        approvalChainStatusWrapper.eq(
                ApprovalChainStatus::getSerialNumber, sysApprovalChain.getSerialNumber());
        approvalChainStatusWrapper.eq(
                ApprovalChainStatus::getSerialNo, sysApprovalChain.getSerialNo());
        approvalChainStatusWrapper
                .eq(ApprovalChainStatus::getPid, sysApprovalChain.getCurrentStatusChildId())
                .last("limit 1");
        ApprovalChainStatus approvalChainStatus =
                approvalChainStatusMapper.selectOne(approvalChainStatusWrapper);
        if (Objects.isNull(approvalChainStatus)) {
            throw new BusinessException(601, "单据记录异常");
        }
        // 更新业务单据记录
        approvalBusiness.setVersionNo(versionNo);
        approvalBusiness.setSerialNumber(approvalProcessDTO.getSerialNumber());
        approvalBusiness.setSerialNo(approvalProcessDTO.getSerialNo());
        approvalBusiness.setRecordId(approvalProcessDTO.getRecordId());
        approvalBusiness.setCurrentStep(1);
        approvalBusiness.setStatusId(sysApprovalChain.getCurrentStatusChildId());
        approvalBusiness.setStatusTitle(approvalChainStatus.getTitle());
        approvalBusiness.setStatusBackground(approvalChainStatus.getStatusBackground());
        approvalBusiness.setStatusFontColor(approvalChainStatus.getStatusFontColor());
        approvalBusiness.setStatusValue(approvalChainStatus.getStatusValue());
        approvalBusiness.setApproverId(sysApprovalChain.getApproverId());
        approvalBusiness.setApproveRoleId(sysApprovalChain.getApproveRoleId());
        approvalBusiness.setApprovalUids(approvalProcessDTO.getApplicantId().toString());
        approvalBusiness.setUpdatedBy(approvalProcessDTO.getApplicantId());
        approvalBusiness.setUpdatedDate(LocalDateTime.now());
        approvalBusiness.setDeleted(false);
        approvalBusinessMapper.updateById(approvalBusiness);

        // 创建业务单据详细内容记录
        ApprovalVersionRecord approvalVersionRecord = new ApprovalVersionRecord();
        approvalVersionRecord.setSubjectId(approvalProcessDTO.getSubjectId());
        approvalVersionRecord.setModuleId(approvalProcessDTO.getModuleId());
        approvalVersionRecord.setRecordId(approvalProcessDTO.getRecordId());
        approvalVersionRecord.setBusinessId(approvalBusiness.getBusinessId());
        approvalVersionRecord.setBusinessNumber(approvalRecord.getBusinessNumber());
        approvalVersionRecord.setVersionNo(versionNo);
        approvalVersionRecord.setSerialNumber(approvalProcessDTO.getSerialNumber());
        approvalVersionRecord.setSerialNo(approvalProcessDTO.getSerialNo());
        approvalVersionRecord.setCurrentStep(1);
        approvalVersionRecord.setStatusId(sysApprovalChain.getCurrentStatusChildId());
        approvalVersionRecord.setStatusTitle(approvalChainStatus.getTitle());
        approvalVersionRecord.setStatusBackground(approvalChainStatus.getStatusBackground());
        approvalVersionRecord.setStatusFontColor(approvalChainStatus.getStatusFontColor());
        approvalVersionRecord.setStatusValue(approvalChainStatus.getStatusValue());
        approvalVersionRecord.setSourceId(sourceId);
        approvalVersionRecord.setApprovalChainTypeId(sysApprovalChain.getApprovalChainTypeId());
        approvalVersionRecord.setCreatedBy(approvalProcessDTO.getApplicantId());
        approvalVersionRecord.setCreatedDate(LocalDateTime.now());
        approvalVersionRecord.setUpdatedBy(approvalProcessDTO.getApplicantId());
        approvalVersionRecord.setUpdatedDate(LocalDateTime.now());
        approvalVersionRecordMapper.insert(approvalVersionRecord);

        // 发送websocket消息
        Map<String, Object> payload = new HashMap<>();
        payload.put("businessNumber", approvalBusiness.getBusinessNumber());
        payload.put("moduleId", approvalBusiness.getModuleId());
        payload.put("recordId", approvalBusiness.getRecordId());
        payload.put("applicantId", approvalProcessDTO.getApplicantId());
        messagePublisher.publish(
                approvalBusiness.getSubjectId(),
                AppContext.getProjectNo(),
                ChangeType.APPROVAL_UPDATE,
                payload);

        approvalProcessDTO.setCurrentStep(approvalBusiness.getCurrentStep());
        approvalProcessDTO.setCreatedBy(approvalBusiness.getCreatedBy());
        approvalProcessDTO.setApprovalUids(approvalBusiness.getApprovalUids());
        approvalProcessDTO.setCreatedDate(approvalBusiness.getCreatedDate());
        // 写入日志
        asyncTaskService.saveAuditLogAsync(AppContext.getProjectNo(), approvalProcessDTO);
        // 发送消息
        asyncTaskService.sendWechatMessageAsync(
                AppContext.getProjectNo(), approvalProcessDTO, 1, "submit", 0L);
    }

    // 审批通过---------------------------------------------------------------------
    @Transactional
    public void businessApprove(ApprovalProcessDTO approvalProcessDTO) {
        Long recordId =
                Objects.nonNull(approvalProcessDTO.getOldRecordId())
                        ? approvalProcessDTO.getOldRecordId()
                        : approvalProcessDTO.getRecordId();
        // 查询当前业务单据记录
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper.eq(ApprovalBusiness::getRecordId, recordId).last("limit 1");
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);
        if (Objects.isNull(approvalBusiness)) {
            throw new BusinessException(601, "单据记录异常");
        }
        approvalProcessDTO.setSerialNo(approvalBusiness.getSerialNo());
        approvalProcessDTO.setSerialNumber(approvalBusiness.getSerialNumber());
        approvalProcessDTO.setCurrentStep(approvalBusiness.getCurrentStep());
        // 已审批人
        List<String> approvalUidList = new ArrayList<>();
        List<String> approvalIdList = new ArrayList<>();
        LambdaQueryWrapper<ApprovalChain> approvalChainWrapper = new LambdaQueryWrapper<>();
        approvalChainWrapper.eq(
                ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
        approvalChainWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
        approvalChainWrapper.eq(ApprovalChain::getSkipped, false);
        approvalChainWrapper.eq(ApprovalChain::getApproved, false);
        approvalChainWrapper
                .eq(ApprovalChain::getCurrentStep, approvalProcessDTO.getCurrentStep())
                .last("LIMIT 1");
        ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalChainWrapper);
        if (Objects.isNull(approvalChain)) {
            throw new BusinessException(601, "单据记录异常");
        }
        if (StringUtils.isNotBlank(approvalChain.getApproverIdList())) {
            approvalIdList =
                    new ArrayList<>(Arrays.asList(approvalChain.getApproverIdList().split(",")));
        }
        if (StringUtils.isNotBlank(approvalChain.getApprovalUids())) {
            approvalUidList =
                    new ArrayList<>(Arrays.asList(approvalBusiness.getApprovalUids().split(",")));
        }
        approvalIdList.add(approvalProcessDTO.getApplicantId().toString());
        approvalChain.setApproverIdList(String.join(",", approvalIdList));
        approvalUidList.add(approvalProcessDTO.getApplicantId().toString());
        approvalChain.setApprovalUids(String.join(",", approvalUidList));
        approvalBusiness.setApprovalUids(String.join(",", approvalUidList));
        approvalChainMapper.updateById(approvalChain);
        // 更新业务主记录
        approvalBusinessMapper.updateById(approvalBusiness);
        // 判断当前步骤是否审批通过
        approvalRule(approvalProcessDTO, approvalChain);
        boolean approved = true;
        if (List.of("all_members_done", "percent_of_group")
                        .contains(approvalChain.getApprovalRule())
                && !approvalProcessDTO.getApplicantId().equals(9999999L)) {
            int percent =
                    "all_members_done".equals(approvalChain.getApprovalRule())
                            ? 100
                            : approvalChain.getRoleApprovalPercent();
            int currentApproverIdNumber =
                    Objects.isNull(approvalChain.getApproverIdList())
                            ? 0
                            : approvalChain.getApproverIdList().split(",").length;
            int allApproverIdNumber =
                    sysUserApi.getUsersByRoleId(approvalChain.getApproveRoleId()).size();
            if (currentApproverIdNumber / allApproverIdNumber < percent / 100) {
                approved = false;
            } else {
                approvalBusiness.setApproverIdList(String.join(",", approvalIdList));
            }
        }
        ApprovalChain approvalChainNext = null;
        if (approved) {
            approvalChain.setApproved(true);
            approvalChainMapper.updateById(approvalChain);
            LambdaQueryWrapper<ApprovalChain> approvalNextWrapper = new LambdaQueryWrapper<>();
            approvalNextWrapper.eq(
                    ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
            approvalNextWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
            approvalNextWrapper
                    .eq(ApprovalChain::getCurrentStep, approvalChain.getNextStep())
                    .last("LIMIT 1");
            approvalChainNext = approvalChainMapper.selectOne(approvalNextWrapper);
            LambdaQueryWrapper<ApprovalChainStatus> approvalChainStatusWrapper =
                    new LambdaQueryWrapper<>();
            approvalChainStatusWrapper.eq(
                    ApprovalChainStatus::getSerialNumber, approvalChain.getSerialNumber());
            approvalChainStatusWrapper.eq(
                    ApprovalChainStatus::getSerialNo, approvalChain.getSerialNo());
            approvalChainStatusWrapper
                    .eq(ApprovalChainStatus::getPid, approvalChain.getNextStatusChildId())
                    .last("limit 1");
            ApprovalChainStatus approvalChainStatus =
                    approvalChainStatusMapper.selectOne(approvalChainStatusWrapper);
            if (Objects.isNull(approvalChainStatus)) {
                throw new BusinessException(601, "单据记录异常");
            }
            if (Objects.nonNull(approvalChainNext)) {
                approvalBusiness.setCurrentStep(approvalChainNext.getCurrentStep());
                approvalBusiness.setStatusId(approvalChainNext.getCurrentStatusChildId());
                approvalBusiness.setStatusTitle(approvalChainStatus.getTitle());
                approvalBusiness.setStatusBackground(approvalChainStatus.getStatusBackground());
                approvalBusiness.setStatusFontColor(approvalChainStatus.getStatusFontColor());
                approvalBusiness.setStatusValue(approvalChainStatus.getStatusValue());
                approvalBusiness.setApprovalChainTypeId(approvalChainNext.getApprovalChainTypeId());
                approvalBusiness.setApproverId(approvalChainNext.getApproverId());
                approvalBusiness.setApproveRoleId(approvalChainNext.getApproveRoleId());
            } else {
                approvalBusiness.setStatusId(approvalChain.getNextStatusChildId());
                approvalBusiness.setStatusTitle(approvalChainStatus.getTitle());
                approvalBusiness.setStatusBackground(approvalChainStatus.getStatusBackground());
                approvalBusiness.setStatusFontColor(approvalChainStatus.getStatusFontColor());
                approvalBusiness.setStatusValue(approvalChainStatus.getStatusValue());
            }
            // 更新业务主记录
            approvalBusinessMapper.updateById(approvalBusiness);

            LambdaQueryWrapper<ApprovalVersionRecord> approvalRecordWrapper =
                    new LambdaQueryWrapper<>();
            approvalRecordWrapper.eq(
                    ApprovalVersionRecord::getSerialNumber, approvalBusiness.getSerialNumber());
            approvalRecordWrapper
                    .eq(ApprovalVersionRecord::getSerialNo, approvalBusiness.getSerialNo())
                    .last("limit 1");
            ApprovalVersionRecord approvalRecord =
                    approvalVersionRecordMapper.selectOne(approvalRecordWrapper);
            if (Objects.isNull(approvalRecord)) {
                throw new BusinessException(601, "单据记录异常");
            }

            approvalRecord.setCurrentStep(approvalBusiness.getCurrentStep());
            approvalRecord.setStatusId(approvalBusiness.getStatusId());
            approvalRecord.setStatusTitle(approvalChainStatus.getTitle());
            approvalRecord.setStatusBackground(approvalChainStatus.getStatusBackground());
            approvalRecord.setStatusFontColor(approvalChainStatus.getStatusFontColor());
            approvalRecord.setStatusValue(approvalChainStatus.getStatusValue());
            approvalVersionRecordMapper.updateById(approvalRecord);
        }

        // 发送websocket消息
        Map<String, Object> payload = new HashMap<>();
        payload.put("businessNumber", approvalBusiness.getBusinessNumber());
        payload.put("moduleId", approvalBusiness.getModuleId());
        payload.put("recordId", approvalBusiness.getRecordId());
        payload.put("applicantId", approvalProcessDTO.getApplicantId());
        messagePublisher.publish(
                approvalBusiness.getSubjectId(),
                AppContext.getProjectNo(),
                ChangeType.APPROVAL_UPDATE,
                payload);

        approvalProcessDTO.setCurrentStep(approvalBusiness.getCurrentStep());
        approvalProcessDTO.setCreatedBy(approvalBusiness.getCreatedBy());
        approvalProcessDTO.setApprovalUids(approvalBusiness.getApprovalUids());
        approvalProcessDTO.setCreatedDate(approvalBusiness.getCreatedDate());
        // 写入日志
        asyncTaskService.saveAuditLogAsync(AppContext.getProjectNo(), approvalProcessDTO);

        // 发送消息
        if (approved) {
            LambdaQueryWrapper<ApprovalChainButton> buttonWrapper = new LambdaQueryWrapper<>();
            buttonWrapper.eq(ApprovalChainButton::getSerialNumber, approvalChain.getSerialNumber());
            buttonWrapper.eq(ApprovalChainButton::getSerialNo, approvalChain.getSerialNo());
            buttonWrapper.eq(ApprovalChainButton::getCurrentStep, approvalChain.getCurrentStep());
            buttonWrapper.eq(ApprovalChainButton::getButtonTypeValue, "pass").last("LIMIT 1");
            ApprovalChainButton button = approvalChainButtonMapper.selectOne(buttonWrapper);
            if (Objects.nonNull(button) && !StringUtils.isBlank(button.getMsgTemplateIds())) {
                String[] ids = button.getMsgTemplateIds().split(",");
                for (String id : ids) {
                    // 发送消息
                    asyncTaskService.sendWechatMessageAsync(
                            AppContext.getProjectNo(),
                            approvalProcessDTO,
                            2,
                            "",
                            Long.parseLong(id));
                }
            }
        }
    }

    // 审批驳回------------------------------------------------------------------
    @Transactional
    public void businessReject(ApprovalProcessDTO approvalProcessDTO) {
        // 查询业务记录
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper
                .eq(ApprovalBusiness::getRecordId, approvalProcessDTO.getRecordId())
                .last("LIMIT 1");
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);
        if (Objects.isNull(approvalBusiness)) {
            throw new BusinessException(601, "单据记录异常");
        }
        approvalProcessDTO.setSerialNo(approvalBusiness.getSerialNo());
        approvalProcessDTO.setSerialNumber(approvalBusiness.getSerialNumber());
        approvalProcessDTO.setCurrentStep(approvalBusiness.getCurrentStep());

        LambdaQueryWrapper<ApprovalVersionRecord> approvalVersionRecordWrapper =
                new LambdaQueryWrapper<>();
        approvalVersionRecordWrapper.eq(
                ApprovalVersionRecord::getSerialNumber, approvalBusiness.getSerialNumber());
        approvalVersionRecordWrapper
                .eq(ApprovalVersionRecord::getSerialNo, approvalBusiness.getSerialNo())
                .last("LIMIT 1");
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(approvalVersionRecordWrapper);
        if (Objects.isNull(approvalVersionRecord)) {
            throw new BusinessException(601, "单据记录异常");
        }
        LambdaQueryWrapper<ApprovalChain> approvalChainWrapper = new LambdaQueryWrapper<>();
        approvalChainWrapper.eq(ApprovalChain::getSerialNumber, approvalBusiness.getSerialNumber());
        approvalChainWrapper.eq(ApprovalChain::getSerialNo, approvalBusiness.getSerialNo());
        approvalChainWrapper.eq(ApprovalChain::getSkipped, false);
        approvalChainWrapper.eq(ApprovalChain::getApproved, false);
        approvalChainWrapper
                .eq(ApprovalChain::getCurrentStep, approvalBusiness.getCurrentStep())
                .last("LIMIT 1");
        ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalChainWrapper);
        if (Objects.isNull(approvalChain)) {
            throw new BusinessException(601, "单据记录异常");
        }
        approvalRule(approvalProcessDTO, approvalChain);
        ApprovalChain businessApprovalChain;
        if (approvalBusiness.getCurrentStep().equals(1)
                || approvalChain.getRejectRule().equals("rejectStart")) {
            approvalChain.setReason(approvalProcessDTO.getReason().toString());
            businessApprovalChain = approvalChain;
            approvalBusiness.setCurrentStep(0);
            approvalVersionRecord.setCurrentStep(0);
            ModuleApprovalChainConfigResp.StatusNode statusNode =
                    sysStatusApi.getStatusByValue(1, approvalProcessDTO.getModuleId());
            if (Objects.isNull(statusNode)) {
                throw new BusinessException(601, "单据记录异常");
            }
            approvalBusiness.setStatusTitle(statusNode.getTitle());
            approvalBusiness.setStatusId(statusNode.getId());
            approvalBusiness.setStatusValue(statusNode.getStatusValue());
            approvalBusiness.setStatusBackground(statusNode.getStatusBackground());
            approvalBusiness.setStatusFontColor(statusNode.getStatusFontColor());
            approvalVersionRecord.setStatusTitle(statusNode.getTitle());
            approvalVersionRecord.setStatusId(statusNode.getId());
            approvalVersionRecord.setStatusValue(statusNode.getStatusValue());
            approvalVersionRecord.setStatusBackground(statusNode.getStatusBackground());
            approvalVersionRecord.setStatusFontColor(statusNode.getStatusFontColor());
        } else {
            Integer specifyStep = approvalChain.getUpStep();
            if (approvalChain.getRejectRule().equals("rejectSpecify")
                    && Objects.nonNull(approvalProcessDTO.getRejectStep())) {
                specifyStep = approvalProcessDTO.getRejectStep();
            }
            ApprovalChain backConfig =
                    approvalChainMapper.selectOne(
                            Wrappers.lambdaQuery(ApprovalChain.class)
                                    .eq(
                                            ApprovalChain::getSerialNumber,
                                            approvalProcessDTO.getSerialNumber())
                                    .eq(
                                            ApprovalChain::getSerialNo,
                                            approvalProcessDTO.getSerialNo())
                                    .eq(ApprovalChain::getSkipped, false)
                                    .eq(ApprovalChain::getCurrentStep, specifyStep)
                                    .last("LIMIT 1"));
            if (Objects.isNull(backConfig)) {
                throw new BusinessException(601, "单据记录异常");
            }
            businessApprovalChain = backConfig;
            LambdaUpdateWrapper<ApprovalChain> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
            updateWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
            updateWrapper.between(
                    ApprovalChain::getCurrentStep, specifyStep, approvalBusiness.getCurrentStep());
            updateWrapper.set(ApprovalChain::getApproved, false);
            updateWrapper.set(ApprovalChain::getApproverIdList, null);
            // updateWrapper.set(ApprovalChain::getApprovalUids, null);
            updateWrapper.set(ApprovalChain::getApprovalUid, null);
            approvalChainMapper.update(updateWrapper);

            approvalBusiness.setCurrentStep(specifyStep);
            approvalVersionRecord.setCurrentStep(specifyStep);
            LambdaQueryWrapper<ApprovalChainStatus> approvalChainStatusWrapper =
                    new LambdaQueryWrapper<>();
            approvalChainStatusWrapper.eq(
                    ApprovalChainStatus::getSerialNumber, approvalChain.getSerialNumber());
            approvalChainStatusWrapper.eq(
                    ApprovalChainStatus::getSerialNo, approvalChain.getSerialNo());
            approvalChainStatusWrapper
                    .eq(ApprovalChainStatus::getPid, backConfig.getCurrentStatusChildId())
                    .last("limit 1");
            ApprovalChainStatus statusNode =
                    approvalChainStatusMapper.selectOne(approvalChainStatusWrapper);
            if (Objects.isNull(statusNode)) {
                throw new BusinessException(601, "单据记录异常");
            }
            approvalBusiness.setStatusTitle(statusNode.getTitle());
            approvalBusiness.setStatusId(backConfig.getCurrentStatusChildId());
            approvalBusiness.setStatusValue(statusNode.getStatusValue());
            approvalBusiness.setStatusBackground(statusNode.getStatusBackground());
            approvalBusiness.setStatusFontColor(statusNode.getStatusFontColor());
            approvalVersionRecord.setStatusTitle(statusNode.getTitle());
            approvalVersionRecord.setStatusId(backConfig.getCurrentStatusChildId());
            approvalVersionRecord.setStatusValue(statusNode.getStatusValue());
            approvalVersionRecord.setStatusBackground(statusNode.getStatusBackground());
            approvalVersionRecord.setStatusFontColor(statusNode.getStatusFontColor());
        }
        approvalBusiness.setApproverId(businessApprovalChain.getApproverId());
        approvalBusiness.setApproveRoleId(businessApprovalChain.getApproveRoleId());
        approvalBusiness.setApproverIdList(null);
        approvalBusiness.setApprovalUids(
                String.join(",", approvalProcessDTO.getApplicantId().toString()));
        approvalBusinessMapper.updateById(approvalBusiness);
        approvalVersionRecordMapper.updateById(approvalVersionRecord);

        // 发送websocket消息
        Map<String, Object> payload = new HashMap<>();
        payload.put("businessNumber", approvalBusiness.getBusinessNumber());
        payload.put("moduleId", approvalBusiness.getModuleId());
        payload.put("recordId", approvalBusiness.getRecordId());
        payload.put("applicantId", approvalProcessDTO.getApplicantId());
        messagePublisher.publish(
                approvalBusiness.getSubjectId(),
                AppContext.getProjectNo(),
                ChangeType.APPROVAL_UPDATE,
                payload);

        approvalProcessDTO.setCurrentStep(approvalBusiness.getCurrentStep());
        approvalProcessDTO.setCreatedBy(approvalBusiness.getCreatedBy());
        approvalProcessDTO.setApprovalUids(approvalBusiness.getApprovalUids());
        approvalProcessDTO.setCreatedDate(approvalBusiness.getCreatedDate());
        // 写入日志
        asyncTaskService.saveAuditLogAsync(AppContext.getProjectNo(), approvalProcessDTO);
        // 发送消息
        LambdaQueryWrapper<ApprovalChainButton> buttonWrapper = new LambdaQueryWrapper<>();
        buttonWrapper.eq(ApprovalChainButton::getSerialNumber, approvalChain.getSerialNumber());
        buttonWrapper.eq(ApprovalChainButton::getSerialNo, approvalChain.getSerialNo());
        buttonWrapper.eq(ApprovalChainButton::getCurrentStep, approvalChain.getCurrentStep());
        buttonWrapper.eq(ApprovalChainButton::getButtonTypeValue, "reject").last("LIMIT 1");
        ApprovalChainButton button = approvalChainButtonMapper.selectOne(buttonWrapper);
        if (Objects.nonNull(button) && !StringUtils.isBlank(button.getMsgTemplateIds())) {
            String[] ids = button.getMsgTemplateIds().split(",");
            for (String id : ids) {
                asyncTaskService.sendWechatMessageAsync(
                        AppContext.getProjectNo(), approvalProcessDTO, 2, "", Long.parseLong(id));
            }
        }
    }

    // 撤销申请-------------------------------------------------------------------------
    @Transactional
    public void businessRevoke(ApprovalProcessDTO approvalProcessDTO) {
        // 查询单据记录
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper
                .eq(ApprovalBusiness::getRecordId, approvalProcessDTO.getRecordId())
                .last("LIMIT 1");
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);
        if (Objects.isNull(approvalBusiness)) {
            throw new BusinessException(601, "单据记录异常");
        }
        // 查询单据详情记录
        approvalProcessDTO.setSerialNo(approvalBusiness.getSerialNo());
        approvalProcessDTO.setSerialNumber(approvalBusiness.getSerialNumber());
        LambdaQueryWrapper<ApprovalVersionRecord> approvalRecordWrapper =
                new LambdaQueryWrapper<>();
        approvalRecordWrapper.eq(
                ApprovalVersionRecord::getSerialNumber, approvalBusiness.getSerialNumber());
        approvalRecordWrapper
                .eq(ApprovalVersionRecord::getSerialNo, approvalBusiness.getSerialNo())
                .last("LIMIT 1");
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(approvalRecordWrapper);
        if (Objects.isNull(approvalVersionRecord)) {
            throw new BusinessException(601, "单据记录异常");
        }
        // 查询取消状态信息
        ModuleApprovalChainConfigResp.StatusNode statusNode =
                sysStatusApi.getStatusByValue(-99, approvalProcessDTO.getModuleId());
        if (Objects.isNull(statusNode)) {
            throw new BusinessException(601, "单据记录异常");
        }
        // 变更过的单据查询来源单据记录，恢复到来源单据信息
        if (approvalBusiness.getVersionNo() > 1) {
            ApprovalVersionRecord oldApprovalVersionRecord =
                    approvalVersionRecordMapper.selectById(approvalVersionRecord.getSourceId());
            if (Objects.isNull(oldApprovalVersionRecord)) {
                throw new BusinessException(601, "单据记录异常");
            }
            LambdaQueryWrapper<ApprovalChain> ApprovalChainWrapper = new LambdaQueryWrapper<>();
            ApprovalChainWrapper.eq(
                    ApprovalChain::getSerialNumber, oldApprovalVersionRecord.getSerialNumber());
            ApprovalChainWrapper.eq(
                    ApprovalChain::getSerialNo, oldApprovalVersionRecord.getSerialNo());
            ApprovalChainWrapper.eq(
                            ApprovalChain::getCurrentStep,
                            oldApprovalVersionRecord.getCurrentStep())
                    .last("LIMIT 1");
            ApprovalChain approvalChain = approvalChainMapper.selectOne(ApprovalChainWrapper);
            if (Objects.isNull(approvalChain)) {
                throw new BusinessException(601, "单据记录异常");
            }
            approvalBusiness.setRecordId(oldApprovalVersionRecord.getRecordId());
            approvalBusiness.setStatusTitle(oldApprovalVersionRecord.getStatusTitle());
            approvalBusiness.setStatusId(oldApprovalVersionRecord.getStatusId());
            approvalBusiness.setStatusValue(oldApprovalVersionRecord.getStatusValue());
            approvalBusiness.setStatusBackground(oldApprovalVersionRecord.getStatusBackground());
            approvalBusiness.setStatusFontColor(oldApprovalVersionRecord.getStatusFontColor());
            approvalBusiness.setCurrentStep(oldApprovalVersionRecord.getCurrentStep());
            approvalBusiness.setStatusValue(oldApprovalVersionRecord.getStatusValue());
            approvalBusiness.setVersionNo(oldApprovalVersionRecord.getVersionNo());
            approvalBusiness.setSerialNumber(oldApprovalVersionRecord.getSerialNumber());
            approvalBusiness.setSerialNo(oldApprovalVersionRecord.getSerialNo());
            approvalBusiness.setApprovalUids(approvalChain.getApprovalUids());
            approvalBusinessMapper.updateById(approvalBusiness);
        } else {
            approvalBusiness.setStatusTitle(statusNode.getTitle());
            approvalBusiness.setStatusId(statusNode.getId());
            approvalBusiness.setStatusValue(statusNode.getStatusValue());
            approvalBusiness.setStatusBackground(statusNode.getStatusBackground());
            approvalBusiness.setStatusFontColor(statusNode.getStatusFontColor());
            approvalBusinessMapper.updateById(approvalBusiness);
        }
        // 变更单据对应版本状态信息
        approvalVersionRecord.setReason(approvalProcessDTO.getReason().toString());
        approvalVersionRecord.setStatusTitle(statusNode.getTitle());
        approvalVersionRecord.setStatusId(statusNode.getId());
        approvalVersionRecord.setStatusValue(statusNode.getStatusValue());
        approvalVersionRecord.setStatusBackground(statusNode.getStatusBackground());
        approvalVersionRecord.setStatusFontColor(statusNode.getStatusFontColor());
        approvalVersionRecordMapper.updateById(approvalVersionRecord);

        // 发送websocket消息
        Map<String, Object> payload = new HashMap<>();
        payload.put("businessNumber", approvalBusiness.getBusinessNumber());
        payload.put("moduleId", approvalBusiness.getModuleId());
        payload.put("recordId", approvalBusiness.getRecordId());
        payload.put("applicantId", approvalProcessDTO.getApplicantId());
        messagePublisher.publish(
                approvalBusiness.getSubjectId(),
                AppContext.getProjectNo(),
                ChangeType.APPROVAL_UPDATE,
                payload);

        approvalProcessDTO.setCurrentStep(approvalBusiness.getCurrentStep());
        approvalProcessDTO.setCreatedBy(approvalBusiness.getCreatedBy());
        approvalProcessDTO.setApprovalUids(approvalBusiness.getApprovalUids());
        approvalProcessDTO.setCreatedDate(approvalBusiness.getCreatedDate());
        // 写入日志
        asyncTaskService.saveAuditLogAsync(AppContext.getProjectNo(), approvalProcessDTO);
        // 发送消息
        asyncTaskService.sendWechatMessageAsync(
                AppContext.getProjectNo(), approvalProcessDTO, 1, "cancel", 0L);
    }

    // =========================辅助方法===============================================
    // 获取业务流审批步骤列表------------------------------------------------------------
    private List<Map<String, Object>> approvalChainStep(
            String serialNumber, int serialNo, Long roleId, Long applicantId) {
        // 1. 构建基础查询条件
        LambdaQueryWrapper<ApprovalChain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalChain::getSerialNumber, serialNumber)
                .eq(ApprovalChain::getPid, 0)
                .eq(ApprovalChain::getSerialNo, serialNo)
                .orderByAsc(ApprovalChain::getCurrentStep);

        // 2. 查询步骤列表 + 关联状态
        List<ApprovalChain> approvalChainlist = approvalChainMapper.selectList(wrapper);

        // 3. 查询当前未审批的最小步骤（当前步骤）
        ApprovalChain currentModel =
                approvalChainMapper.selectOne(
                        new LambdaQueryWrapper<ApprovalChain>()
                                .eq(ApprovalChain::getSerialNumber, serialNumber)
                                .eq(ApprovalChain::getSerialNo, serialNo)
                                .eq(ApprovalChain::getApproved, false)
                                .orderByAsc(ApprovalChain::getCurrentStep)
                                .last("LIMIT 1"));

        // 4. 判断是否最后一步
        int isEnd = currentModel == null ? 1 : 0;

        List<Map<String, Object>> data = new ArrayList<>();
        int x = 1;
        int length = approvalChainlist.size();

        // 5. 遍历步骤
        for (int index = 0; index < approvalChainlist.size(); index++) {
            ApprovalChain item = approvalChainlist.get(index);
            int isHighlight = 0;

            // 高亮判断
            if (isEnd == 0 && currentModel != null) {
                if (item.getId().equals(currentModel.getId())) {
                    isHighlight = 1;
                }
            }

            // 当前角色是否可操作
            int isOperate = 0;
            if (Objects.nonNull(item.getApproverId())) {
                if (applicantId.equals(item.getApproverId())
                        && roleId.equals(item.getApproveRoleId())) {
                    isOperate = 1;
                }
            } else if (roleId.equals(item.getApproveRoleId())) {
                isOperate = 1;
            }

            LambdaQueryWrapper<ApprovalChainStatus> approvalChainStatusWrapper =
                    new LambdaQueryWrapper<>();
            approvalChainStatusWrapper.eq(
                    ApprovalChainStatus::getSerialNumber, item.getSerialNumber());
            approvalChainStatusWrapper.eq(ApprovalChainStatus::getSerialNo, item.getSerialNo());
            approvalChainStatusWrapper
                    .eq(ApprovalChainStatus::getPid, item.getCurrentStatusChildId())
                    .last("limit 1");
            ApprovalChainStatus status =
                    approvalChainStatusMapper.selectOne(approvalChainStatusWrapper);
            // 组装步骤数据
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getCurrentStatusChildId());
            map.put("order", x);
            map.put("title", status == null ? "" : status.getTitle());
            map.put("statusBackground", status == null ? "" : status.getStatusBackground());
            map.put("statusFontColor", status == null ? "" : status.getStatusFontColor());
            map.put("statusValue", status == null ? "" : status.getStatusValue());
            map.put("isApproved", item.getApproved());
            map.put("isHighlight", isHighlight);
            map.put("isOperate", isOperate);
            map.put("canDo", (isHighlight == 1 && isOperate == 1) ? 1 : 0);
            map.put("isCollaboration", item.getCollaborated());
            map.put("approvalRule", item.getApprovalRule());
            data.add(map);

            // 6. 最后一步追加【下一状态】
            if (x == length) {
                LambdaQueryWrapper<ApprovalChainStatus> approvalNextStatusWrapper =
                        new LambdaQueryWrapper<>();
                approvalNextStatusWrapper.eq(
                        ApprovalChainStatus::getSerialNumber, item.getSerialNumber());
                approvalNextStatusWrapper.eq(ApprovalChainStatus::getSerialNo, item.getSerialNo());
                approvalNextStatusWrapper
                        .eq(ApprovalChainStatus::getPid, item.getNextStatusChildId())
                        .last("limit 1");
                ApprovalChainStatus model =
                        approvalChainStatusMapper.selectOne(approvalNextStatusWrapper);
                if (model == null) {
                    throw new BusinessException(600, item.getNextStatusChildId() + "此状态已删除.");
                }

                int lastHighlight = 0;
                if (isEnd == 1 && item.getApproved()) {
                    lastHighlight = 1;
                }

                Map<String, Object> lastMap = new HashMap<>();
                lastMap.put("id", model.getId());
                lastMap.put("order", x + 1);
                lastMap.put("title", model.getTitle());
                lastMap.put("statusBackground", model.getStatusBackground());
                lastMap.put("statusFontColor", model.getStatusFontColor());
                lastMap.put("statusValue", model.getStatusValue());
                lastMap.put("isApproved", item.getApproved());
                lastMap.put("isHighlight", lastHighlight);
                lastMap.put("canDo", 0);
                lastMap.put("isCollaboration", item.getCollaborated());
                lastMap.put("approvalRule", item.getApprovalRule());
                data.add(lastMap);
            }
            x++;
        }

        return data;
    }

    // 查询审批步骤绑定的操作按钮------------------------------------------------------------------
    private List<Map<String, Object>> getOperationButton(
            ApprovalProcessReq approvalProcessReq, AtomicReference<Integer> canEdit) {
        String btnType = approvalProcessReq.getButton();
        List<Map<String, Object>> operations = new ArrayList<>();
        if (Objects.nonNull(btnType) && (btnType.equals("change-view"))) {
            return operations;
        }
        Long userId = approvalProcessReq.getApplicantId();
        Long roleId = approvalProcessReq.getRoleId();
        // 查询单据当前信息
        LambdaQueryWrapper<ApprovalBusiness> businessWrapper = new LambdaQueryWrapper<>();
        businessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleId());
        businessWrapper
                .eq(ApprovalBusiness::getRecordId, approvalProcessReq.getRecordId())
                .last("LIMIT 1");
        ;
        ApprovalBusiness approvalChainBusiness = approvalBusinessMapper.selectOne(businessWrapper);

        if (Objects.isNull(approvalChainBusiness)) {
            return operations;
        }
        // 加载变更页面时显示提交按钮
        if (Objects.nonNull(btnType)
                && (btnType.equals("change-change") || btnType.equals("change"))) {
            operations.add(
                    showButtonInfo(
                            "submit",
                            null,
                            approvalChainBusiness.getSerialNumber(),
                            approvalChainBusiness.getSerialNo(),
                            approvalChainBusiness.getCurrentStep()));
            canEdit.set(1);
            return operations;
        }
        ApprovalProcessDTO approvalProcessDTO = convertApprovalDTO(approvalProcessReq);
        approvalProcessDTO.setSerialNumber(approvalChainBusiness.getSerialNumber());
        approvalProcessDTO.setSerialNo(approvalChainBusiness.getSerialNo());
        approvalProcessDTO.setCurrentStep(approvalChainBusiness.getCurrentStep());
        LambdaQueryWrapper<ApprovalChain> approvalWrapper = new LambdaQueryWrapper<>();
        approvalWrapper.eq(ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
        approvalWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
        approvalWrapper
                .eq(ApprovalChain::getCurrentStep, approvalProcessDTO.getCurrentStep())
                .last("LIMIT 1");
        ;
        ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalWrapper);
        if (approvalChainBusiness.getStatusValue().equals(99)) {
            // 查询时如果启用状态有权限显示变更按钮
            if (approvalChainBusiness.getCreatedBy().equals(userId)
                    && checkRight(approvalProcessDTO)) {
                operations.add(
                        showButtonInfo(
                                "change",
                                null,
                                approvalChainBusiness.getSerialNumber(),
                                approvalChainBusiness.getSerialNo(),
                                approvalChainBusiness.getCurrentStep()));
            } else {
                return operations;
            }
        } else if (approvalChainBusiness.getStatusValue() != -99) {
            if (approvalChainBusiness.getCurrentStep().equals(0)) {
                // 如果单据处于第一步驳回后申请人等待提交状态,显示提交和取消按钮
                if (approvalChainBusiness.getCreatedBy().equals(userId)
                        && checkRight(approvalProcessDTO)) {
                    operations.add(
                            showButtonInfo(
                                    "submit",
                                    null,
                                    approvalChainBusiness.getSerialNumber(),
                                    approvalChainBusiness.getSerialNo(),
                                    approvalChainBusiness.getCurrentStep()));
                    canEdit.set(1);
                }
            } else if ((Objects.nonNull(approvalChainBusiness.getApproverId())
                            && approvalChainBusiness.getApproverId().equals(userId))
                    || (Objects.nonNull(approvalChainBusiness.getApproveRoleId())
                            && approvalChainBusiness.getApproveRoleId().equals(roleId))) {
                boolean btnShow = true;
                // 判断用户是否已经完成会签
                if (List.of("all_members_done", "percent_of_group")
                                .contains(approvalChain.getApprovalRule())
                        && Objects.nonNull(approvalChain.getApproverIdList())) {
                    List<String> ids = List.of(approvalChain.getApproverIdList().split(","));
                    if (ids.contains(userId.toString())) {
                        btnShow = false;
                    }
                }
                if (btnShow) {
                    // 如果单据处于等待审批状态,显示该步骤绑定的按钮
                    LambdaQueryWrapper<ApprovalChainButton> approvalButtonWrapper =
                            new LambdaQueryWrapper<>();
                    approvalButtonWrapper.eq(
                            ApprovalChainButton::getSerialNumber,
                            approvalProcessDTO.getSerialNumber());
                    approvalButtonWrapper.eq(
                            ApprovalChainButton::getSerialNo, approvalProcessDTO.getSerialNo());
                    approvalButtonWrapper.eq(
                            ApprovalChainButton::getCurrentStep,
                            approvalChainBusiness.getCurrentStep());
                    approvalButtonWrapper.orderByAsc(ApprovalChainButton::getButtonId);
                    List<ApprovalChainButton> buttonList =
                            approvalChainButtonMapper.selectList(approvalButtonWrapper);
                    for (ApprovalChainButton item : buttonList) {
                        operations.add(
                                showButtonInfo(
                                        item.getButtonTypeValue(),
                                        item.getButtonId(),
                                        approvalChainBusiness.getSerialNumber(),
                                        approvalChainBusiness.getSerialNo(),
                                        approvalChainBusiness.getCurrentStep()));
                    }
                    if (!operations.isEmpty()) {
                        canEdit.set(1);
                    }
                }
            }
            if (approvalChainBusiness.getStatusValue() != -99
                    && approvalChainBusiness.getCreatedBy().equals(userId)
                    && checkRight(approvalProcessDTO)) {
                operations.add(
                        showButtonInfo(
                                "cancel",
                                null,
                                approvalChainBusiness.getSerialNumber(),
                                approvalChainBusiness.getSerialNo(),
                                approvalChainBusiness.getCurrentStep()));
            }
        }
        return operations;
    }

    // 获取版本列表，处理变更模式--------------------------------------------------------------
    protected List<Object> getVersionList(String getBusinessNumber, String btnType) {
        // 1. 获取数据
        LambdaQueryWrapper<ApprovalVersionRecord> wrapper =
                new LambdaQueryWrapper<ApprovalVersionRecord>()
                        .eq(ApprovalVersionRecord::getBusinessNumber, getBusinessNumber)
                        .select(
                                ApprovalVersionRecord::getId,
                                ApprovalVersionRecord::getRecordId,
                                ApprovalVersionRecord::getVersionNo,
                                ApprovalVersionRecord::getCurrentStep,
                                ApprovalVersionRecord::getStatusValue)
                        .orderByDesc(
                                ApprovalVersionRecord::getVersionNo,
                                ApprovalVersionRecord::getCurrentStep);
        List<ApprovalVersionRecord> versionDataList =
                approvalVersionRecordMapper.selectList(wrapper);

        // 2. 组装版本列表
        List<Map<String, Object>> versionList = new ArrayList<>();
        Integer currentVersionNo = 0;
        for (ApprovalVersionRecord item : versionDataList) {
            if (currentVersionNo.equals(item.getVersionNo())) {
                continue;
            }
            currentVersionNo = item.getVersionNo();
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("recordId", item.getRecordId());
            map.put("versionNo", item.getVersionNo());
            map.put("statusValue", item.getStatusValue());
            map.put("btnType", btnType.contains("change") ? "change-view" : "");
            versionList.add(map);
        }

        int versionNo = 0;

        // 3. 处理 change 模式：追加一个新版本
        if (btnType.contains("change") && CollectionUtil.isNotEmpty(versionList)) {
            // 过滤出 status_value = 99 的数据
            List<Map<String, Object>> sortData =
                    versionList.stream()
                            .filter(item -> Objects.equals(item.get("statusValue"), 99))
                            .collect(Collectors.toList());

            if (CollectionUtil.isNotEmpty(sortData)) {
                // 按 id 倒序
                sortData.sort((a, b) -> Long.compare(((Long) b.get("id")), ((Long) a.get("id"))));
                // 取第一条
                Map<String, Object> lastData = new HashMap<>(sortData.get(0));
                lastData.put("id", 0);
                lastData.put("recordId", sortData.get(0).get("recordId"));
                lastData.put("statusValue", 1);
                lastData.put("btnType", "change-change");
                versionNo = versionList.size() + 1;
                lastData.put("versionNo", versionNo);
                versionList.add(lastData);
            }
        }

        // 4. 按 version_no 倒序
        versionList.sort(
                (a, b) ->
                        Short.compare(
                                (Short.parseShort(b.get("versionNo").toString())),
                                Short.parseShort(a.get("versionNo").toString())));

        // 5. 返回 [versionList, versionNo]
        List<Object> result = new ArrayList<>();
        result.add(versionList);
        return result;
    }

    // 获取按钮完整信息（样式、标题、图标、排序等）------------------------------------------------
    private Map<String, Object> showButtonInfo(
            String alias,
            Long buttonId,
            String getSerialNumber,
            Integer getSerialNo,
            Integer currentStep) {
        Map<String, Object> item = new HashMap<>();
        // 1. 查询按钮配置
        ApprovalChainButton approvalButton = null;
        SysButtonResp modelConfigButton = null;
        SysConfigItemResp modelConfig = null;
        if (Objects.nonNull(buttonId)) {
            LambdaQueryWrapper<ApprovalChainButton> approvalButtonWrapper =
                    new LambdaQueryWrapper<>();
            approvalButtonWrapper.eq(ApprovalChainButton::getButtonId, buttonId);
            approvalButtonWrapper.eq(ApprovalChainButton::getSerialNumber, getSerialNumber);
            approvalButtonWrapper.eq(ApprovalChainButton::getSerialNo, getSerialNo);
            approvalButtonWrapper
                    .eq(ApprovalChainButton::getCurrentStep, currentStep)
                    .last("LIMIT 1");
            approvalButton = approvalChainButtonMapper.selectOne(approvalButtonWrapper);
            modelConfigButton = sysButtonApi.getButtonById(buttonId);
            if (Objects.isNull(modelConfigButton) || Objects.isNull(approvalButton)) {
                return null;
            }
            item.put("buttonKey", alias);
            item.put("title", modelConfigButton.getTitle());
            item.put("showTitle", modelConfigButton.getTitle());
            Map<String, Object> property = new HashMap<>();
            property.put("defaultBgColor", modelConfigButton.getDefaultBgColor());
            property.put("defaultFontColor", modelConfigButton.getDefaultFontColor());
            property.put("levitateBgColor", modelConfigButton.getLevitateBgColor());
            property.put("levitateFontColor", modelConfigButton.getLevitateFontColor());
            property.put("selectedBgColor", modelConfigButton.getSelectedBgColor());
            property.put("selectedFontColor", modelConfigButton.getSelectedFontColor());
            item.put("extraProperty", property);
            item.put("actionIcon", approvalButton.getIcon());
            item.put("actionName", approvalButton.getLogName());
            if (alias.equals("reject")) {
                // 查询当前审批步骤
                ApprovalChain approvalChain =
                        approvalChainMapper.selectOne(
                                new LambdaQueryWrapper<ApprovalChain>()
                                        .eq(ApprovalChain::getSerialNumber, getSerialNumber)
                                        .eq(ApprovalChain::getSerialNo, getSerialNo)
                                        .eq(ApprovalChain::getCurrentStep, currentStep)
                                        .last("LIMIT 1"));
                item.put("rejectRule", approvalChain.getRejectRule());
            } else {
                item.put("rejectRule", "");
            }
            item.put("autoNextTaskFlag", approvalButton.getAutoNextTaskFlag());
        } else {
            modelConfig = sysConfigCategoryApi.getConfigItemsByValue("ButtonStyle", alias);
            if (Objects.isNull(modelConfig)) {
                return null;
            }
            item.put("buttonKey", alias);
            item.put("title", modelConfig.getLabel());
            item.put("showTitle", modelConfig.getLabel());
            String extraPropertyStr = modelConfig.getExtra();
            if (Objects.nonNull(extraPropertyStr) && !extraPropertyStr.isEmpty()) {
                Type type = new TypeReference<Map<String, Object>>() {};
                Map<String, Object> extraProperty =
                        BeanUtil.beanToMap(JSONUtil.toBean(extraPropertyStr, type, true));
                Map<String, Object> property = new HashMap<>();
                property.put("defaultBgColor", MapUtil.getStr(extraProperty, "default_bg_color"));
                property.put(
                        "defaultFontColor", MapUtil.getStr(extraProperty, "default_font_color"));
                property.put("levitateBgColor", MapUtil.getStr(extraProperty, "levitate_bg_color"));
                property.put(
                        "levitateFontColor", MapUtil.getStr(extraProperty, "levitate_font_color"));
                property.put("selectedBgColor", MapUtil.getStr(extraProperty, "selected_bg_color"));
                property.put(
                        "selectedFontColor", MapUtil.getStr(extraProperty, "selected_font_color"));
                item.put("extraProperty", property);
            } else {
                Map<String, Object> property = new HashMap<>();
                property.put("defaultBgColor", "");
                property.put("defaultFontColor", "");
                property.put("levitateBgColor", "");
                property.put("levitateFontColor", "");
                property.put("selectedBgColor", "");
                property.put("selectedFontColor", "");
                item.put("extraProperty", property);
            }
            item.put("actionIcon", modelConfig.getValue());
            item.put("actionName", "已" + modelConfig.getLabel());
            item.put("rejectRule", "");
            item.put("autoNextTaskFlag", 0);
        }
        item.put("show", 1);

        return item;
    }

    // 检查角色是否拥有模块的写入权限----------------------------------------------
    private Boolean checkRight(ApprovalProcessDTO approvalProcessDTO) {
        return sysRolePermissionApi.checkRoleHasModuleWrite(
                approvalProcessDTO.getRoleId(), approvalProcessDTO.getModuleId());
    }

    // 新增或者变更业务时初始化审批链-------------------------------------------------------
    @Transactional
    protected void initApprovalChainForModule(ApprovalProcessDTO approvalProcessDTO) {
        Long userId = approvalProcessDTO.getApplicantId();
        // 1. 查询审批链配置列表
        ModuleApprovalChainConfigResp approvalConfig =
                sysApprovalChainConfigApi.getApprovalConfig(
                        approvalProcessDTO.getModuleId(), approvalProcessDTO.getApprovalTypeId());
        if (approvalConfig.getApprovalChainConfigList().isEmpty())
            throw new BusinessException(600, "审批链已发生变化,页面将刷新!");
        // 2. 遍历生成审批链
        for (ModuleApprovalChainConfigResp.ApprovalChainConfigDetail item :
                approvalConfig.getApprovalChainConfigList()) {

            ApprovalChain insertChainItem = new ApprovalChain();

            BeanUtil.copyProperties(item, insertChainItem);

            insertChainItem.setPid(0L);
            insertChainItem.setSerialNumber(approvalProcessDTO.getSerialNumber());
            insertChainItem.setSerialNo(approvalProcessDTO.getSerialNo());
            insertChainItem.setCurrentStatusChildId(item.getCurrentStatusId());
            insertChainItem.setNextStatusChildId(item.getNextStatusId());
            insertChainItem.setCreatedBy(userId);
            insertChainItem.setUpdatedBy(userId);
            insertChainItem.setSendedMsg(false);
            insertChainItem.setId(null);
            insertChainItem.setButtonList(null);
            insertChainItem.setPidModuleHash("");

            approvalChainMapper.insert(insertChainItem);
            Long sysApprovalChainId = insertChainItem.getId();

            // 3. 处理按钮列表
            List<ApprovalChainButton> buttonDataList = new ArrayList<>();
            for (ModuleApprovalChainConfigResp.ButtonConfigDetail button :
                    approvalConfig.getButtonList()) {
                if (item.getButtonList().matches(".*\\b" + button.getId() + "\\b.*")) {
                    ApprovalChainButton insertItem = new ApprovalChainButton();
                    BeanUtils.copyProperties(button, insertItem);
                    SysConfigItemResp modelConfig =
                            sysConfigCategoryApi.getConfigItemsById(button.getButtonTypeId());
                    if (Objects.nonNull(modelConfig)) {
                        insertItem.setButtonTypeValue(modelConfig.getValue());
                    } else {
                        insertItem.setButtonTypeValue(button.getIcon());
                    }
                    insertItem.setPid(sysApprovalChainId);
                    insertItem.setSerialNumber(approvalProcessDTO.getSerialNumber());
                    insertItem.setSerialNo(approvalProcessDTO.getSerialNo());
                    insertItem.setId(null);
                    insertItem.setCurrentStep(item.getCurrentStep());
                    buttonDataList.add(insertItem);
                }
            }
            // 批量插入按钮
            if (!buttonDataList.isEmpty()) approvalChainButtonMapper.insert(buttonDataList);

            // 4. 处理状态列表
            List<ApprovalChainStatus> statusDataList = new ArrayList<>();
            for (ModuleApprovalChainConfigResp.StatusNode statusNode :
                    approvalConfig.getStatusList()) {
                if (item.getCurrentStatusId().equals(statusNode.getId())) {
                    ApprovalChainStatus insertItem = new ApprovalChainStatus();
                    BeanUtils.copyProperties(statusNode, insertItem);
                    insertItem.setPid(item.getCurrentStatusId());
                    insertItem.setId(null);
                    insertItem.setCurrentStep(item.getCurrentStep());
                    insertItem.setSerialNumber(approvalProcessDTO.getSerialNumber());
                    insertItem.setSerialNo(approvalProcessDTO.getSerialNo());
                    statusDataList.add(insertItem);
                    continue;
                }
                if (item.getNextStatusId().equals(statusNode.getId())) {
                    ApprovalChainStatus insertItem = new ApprovalChainStatus();
                    BeanUtils.copyProperties(statusNode, insertItem);
                    insertItem.setPid(item.getNextStatusId());
                    insertItem.setId(null);
                    insertItem.setCurrentStep(item.getCurrentStep());
                    insertItem.setSerialNumber(approvalProcessDTO.getSerialNumber());
                    insertItem.setSerialNo(approvalProcessDTO.getSerialNo());
                    statusDataList.add(insertItem);
                }
            }
            // 批量插入状态
            if (!statusDataList.isEmpty()) approvalChainStatusMapper.insert(statusDataList);
        }
    }

    // 审批规则判断-----------------------------------------------------------------------
    private void approvalRule(
            ApprovalProcessDTO approvalProcessDTO, @NonNull ApprovalChain approvalChain) {
        switch (approvalChain.getApprovalRule()) {
            case "single_approval":
                if (!approvalProcessDTO.getApplicantId().equals(approvalChain.getApproverId())) {
                    throw new BusinessException(600, "审批链已发生变化,页面将刷新!");
                }
                break;
            case "one_of_role_group":
            case "all_data_done_role_group":
            case "all_number_done_role_group":
            case "all_members_done":
            case "percent_of_group":
                if (!approvalProcessDTO.getRoleId().equals(approvalChain.getApproveRoleId())) {
                    throw new BusinessException(600, "审批链已发生变化,页面将刷新!");
                }
        }
    }

    // ApprovalProcessReq转换为ApprovalProcessDTO----------------------------------------
    private ApprovalProcessDTO convertApprovalDTO(ApprovalProcessReq approvalProcessReq) {
        ApprovalProcessDTO approvalProcessDTO = new ApprovalProcessDTO();
        BeanUtils.copyProperties(approvalProcessReq, approvalProcessDTO);
        return approvalProcessDTO;
    }

    private ApprovalProcessDTO convertApprovalDTO(ApprovalEvent event) {
        ApprovalProcessDTO approvalProcessDTO = new ApprovalProcessDTO();
        BeanUtils.copyProperties(event, approvalProcessDTO);
        return approvalProcessDTO;
    }

    // 转换对象--------------------------------------------------------------------------
    private Map<String, Object> convertMapResp(
            ApprovalBusiness approvalBusiness, Integer approvalType) {
        // Map<String, Object> approvalListResp = new HashMap<>();
        // approvalListResp.put("recordId", approvalBusiness.getRecordId());
        // Map<String, Object> businessNo = new HashMap<>();
        // businessNo.put("businessNumber", approvalBusiness.getBusinessNumber());
        // businessNo.put("versionNo", approvalBusiness.getVersionNo());
        // approvalListResp.put("businessNo", businessNo);
        // approvalListResp.put("createUser", convertUserResp(approvalBusiness.getCreatedBy()));
        // approvalListResp.put("createdDate", approvalBusiness.getCreatedDate());
        //        Map<String, Object> approvalStatus = new HashMap<>();
        //        approvalStatus.put("id", approvalBusiness.getStatusId());
        //        approvalStatus.put("statusValue", approvalBusiness.getStatusValue());
        //        approvalStatus.put("title", approvalBusiness.getStatusTitle());
        //        approvalStatus.put("statusBackground", approvalBusiness.getStatusBackground());
        //        approvalStatus.put("statusFontColor", approvalBusiness.getStatusFontColor());
        //        approvalListResp.put("approvalStatus", approvalStatus);
        Map<String, Object> btnInfo = new LinkedHashMap<>();
        String alias = "view";
        String btnName = "查阅";
        if (approvalType == 1) {
            alias = "handle";
            btnName = "办理";
        }
        btnInfo.put("recordId", approvalBusiness.getRecordId());
        btnInfo.put("buttonKey", alias);
        SysConfigItemResp modelConfig =
                sysConfigCategoryApi.getConfigItemsByValue("ListButtonStyle", alias);
        if (Objects.nonNull(modelConfig) && Objects.nonNull(modelConfig.getLabel())) {
            btnInfo.put("buttonName", modelConfig.getLabel());
        } else {
            btnInfo.put("buttonName", btnName);
        }
        // List<Map<String, Object>> btnList = new ArrayList<>();
        // btnList.add(btnInfo);
        // approvalListResp.put("btnInfo", btnInfo);
        return btnInfo;
    }

    private ActionLogResp.UserVO convertUserResp(Long userId) {
        UserDetailResp createByUser = new UserDetailResp();
        if (!userId.equals(9999999L)) {
            try {
                createByUser = sysUserApi.getUserDetailByUserId(userId);
            } catch (Exception e) {
                createByUser.setId(userId);
                createByUser.setUserName("");
            }
        } else {
            createByUser.setId(9999999L);
            createByUser.setUserName("系统自动审批");
        }
        ActionLogResp.UserVO userVO = new ActionLogResp.UserVO();
        if (createByUser != null) {
            userVO.setId(createByUser.getUserId());
            userVO.setUserName(createByUser.getUserName());
            userVO.setUserAvatar(createByUser.getUserAvatar());
            userVO.setDepartName(createByUser.getDepartName());
            userVO.setPositionName(createByUser.getPositionName());
            userVO.setWorkNumber(createByUser.getWorkNumber());
        }
        return userVO;
    }
}
