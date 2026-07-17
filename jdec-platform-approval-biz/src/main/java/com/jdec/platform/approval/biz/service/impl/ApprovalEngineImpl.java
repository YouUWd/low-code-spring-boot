package com.jdec.platform.approval.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.req.ApprovalDataReq;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.approval.api.req.SaveBusinessIdReq;
import com.jdec.platform.approval.api.resp.ActionLogResp;
import com.jdec.platform.approval.biz.dto.ApprovalProcessDTO;
import com.jdec.platform.approval.biz.entity.*;
import com.jdec.platform.approval.biz.mapper.*;
import com.jdec.platform.approval.biz.util.ToolUtils;
import com.jdec.platform.config.api.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.STUDENT_MANAGE)
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
    private final ApprovalActionLogMapper approvalActionLogMapper;
    private final SysRolePermissionApi sysRolePermissionApi;

    @Override
    @Transactional
    public Map<String, Object> process(ApprovalProcessReq approvalProcessReq) {
        ApprovalProcessDTO approvalProcessDTO = convertApprovalDTO(approvalProcessReq);
        if (List.of("submit", "edit", "change").contains(approvalProcessDTO.getButton())) {
            businessSubmit(approvalProcessDTO);
        } else if ("create".equals(approvalProcessDTO.getButton())) {
            businessCreate(approvalProcessDTO);
        } else if ("pass".equals(approvalProcessDTO.getButton())) {
            Integer statusValue = businessApprove(approvalProcessDTO);
            if (statusValue.equals(99)) {
                return Map.of("ok", 1, "end", 1);
            }
        } else if ("reject".equals(approvalProcessDTO.getButton())) {
            businessReject(approvalProcessDTO);
        } else if ("cancel".equals(approvalProcessDTO.getButton())) {
            businessRevoke(approvalProcessDTO);
        }

        return Map.of("ok", 1, "end", 0);
    }

    @Override
    @Transactional
    public Map<String, Object> saveBusinessId(SaveBusinessIdReq req) {
        LambdaUpdateWrapper<ApprovalBusiness> businessWrapper = new LambdaUpdateWrapper<>();
        businessWrapper.eq(ApprovalBusiness::getModuleId, req.getModuleId());
        businessWrapper.eq(ApprovalBusiness::getRecordId, req.getRecordId());
        businessWrapper.set(ApprovalBusiness::getBusinessId, req.getBusinessId());
        int businessResult = approvalBusinessMapper.update(businessWrapper);

        LambdaUpdateWrapper<ApprovalVersionRecord> recordWrapper = new LambdaUpdateWrapper<>();
        recordWrapper.eq(ApprovalVersionRecord::getModuleId, req.getModuleId());
        recordWrapper.eq(ApprovalVersionRecord::getRecordId, req.getRecordId());
        recordWrapper.set(ApprovalVersionRecord::getBusinessId, req.getBusinessId());
        int recordResult = approvalVersionRecordMapper.update(recordWrapper);
        return Map.of("businessResult", businessResult, "recordResult", recordResult);
    }

    // 查询单据审批详细数据--------------------------------------------------------------------
    @Override
    public Map<String, Object> getApprovalDetail(ApprovalProcessReq req) {
        List<Map<String, Object>> step = getApprovalStep(req);
        List<Map<String, Object>> operationButton = getOperationButton(req);
        LambdaQueryWrapper<ApprovalVersionRecord> businessWrapper = new LambdaQueryWrapper<>();
        businessWrapper.eq(ApprovalVersionRecord::getModuleId, req.getModuleId());
        businessWrapper.eq(ApprovalVersionRecord::getRecordId, req.getRecordId());
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(businessWrapper);
        Map<String, Object> approvalInfo = BeanUtil.beanToMap(approvalVersionRecord, true, true);
        UserDetailResp createByUser =
                sysUserApi.getUserDetailByUserId(approvalVersionRecord.getCreatedBy());
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("created_by_name", createByUser.getUserName());
        userInfo.put("u_avatar", createByUser.getUserAvatar());
        userInfo.put("u_work_number", createByUser.getWorkNumber());
        approvalInfo.put("user", userInfo);
        Object[] versionArr =
                getVersionList(approvalVersionRecord.getBusinessNumber(), req.getButton())
                        .toArray();
        List<Map<String, Object>> versionList = (List<Map<String, Object>>) versionArr[0];
        approvalInfo.put("version_list", versionList);
        // 11. 最终返回
        Map<String, Object> result = new HashMap<>();
        result.put("info", approvalInfo);
        result.put("step", step);
        result.put("button", operationButton);
        return result;
    }

    // 查询操作人在当前模块下的审批链业务代办/已办/全部数量-------------------------------------------
    @Override
    public Map<String, Object> getBusinessTypeList(ApprovalProcessReq approvalProcessReq) {
        LambdaQueryWrapper<ApprovalBusiness> todoWrapper = new LambdaQueryWrapper<>();
        todoWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleId());
        todoWrapper.eq(ApprovalBusiness::getSubjectId, AppContext.getSubjectId());
        todoWrapper
                .nested(
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
                                                approvalProcessReq.getRoleId()));
        todoWrapper.notIn(ApprovalBusiness::getStatusValue, 99, -99);
        List<ApprovalBusiness> todoList = approvalBusinessMapper.selectList(todoWrapper);
        LambdaQueryWrapper<ApprovalBusiness> processWrapper = new LambdaQueryWrapper<>();
        processWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleId());
        processWrapper.eq(ApprovalBusiness::getSubjectId, approvalProcessReq.getSubjectId());
        // processWrapper.eq(ApprovalBusiness::getApprovalUids,
        // approvalProcessReq.getApplicantId());
        processWrapper.apply(
                "FIND_IN_SET({0}, approval_uids)", approvalProcessReq.getApplicantId());
        List<ApprovalBusiness> processList = approvalBusinessMapper.selectList(processWrapper);
        LambdaQueryWrapper<ApprovalBusiness> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleId());
        allWrapper.eq(ApprovalBusiness::getSubjectId, approvalProcessReq.getSubjectId());
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("todo_num", todoList.size());
        resultMap.put("done_num", processList.size());
        resultMap.put("all_num", Math.toIntExact(approvalBusinessMapper.selectCount(allWrapper)));
        return resultMap;
    }

    @Override
    public List<Map<String, Object>> getBusinessList(ApprovalListReq approvalListReq) {
        List<ApprovalBusiness> toDoList = new ArrayList<>();
        List<Map<String, Object>> respList = new ArrayList<>();
        if (approvalListReq.getApprovalType() != 2) {
            LambdaQueryWrapper<ApprovalBusiness> todoWrapper = new LambdaQueryWrapper<>();
            todoWrapper.in(ApprovalBusiness::getModuleId, approvalListReq.getModuleIdList());
            todoWrapper.in(ApprovalBusiness::getSubjectId, approvalListReq.getSubjectIdList());
            todoWrapper
                    .nested(
                            wp1 ->
                                    wp1.eq(
                                                    ApprovalBusiness::getApproverId,
                                                    approvalListReq.getApplicantId())
                                            .eq(
                                                    ApprovalBusiness::getApproveRoleId,
                                                    approvalListReq.getRoleId()))
                    .or()
                    .nested(
                            wp2 ->
                                    wp2.isNull(ApprovalBusiness::getApproverId)
                                            .eq(
                                                    ApprovalBusiness::getApproveRoleId,
                                                    approvalListReq.getRoleId()));
            todoWrapper.notIn(ApprovalBusiness::getStatusValue, 99, -99);
            todoWrapper.in(ApprovalBusiness::getRecordId, approvalListReq.getRecordIdList());
            toDoList = approvalBusinessMapper.selectList(todoWrapper);
            if (approvalListReq.getApprovalType() == 1) {
                respList =
                        toDoList.stream()
                                .map(business -> convertMapResp(business, 1))
                                .collect(Collectors.toList());
            }
        } else {
            LambdaQueryWrapper<ApprovalBusiness> processWrapper = new LambdaQueryWrapper<>();
            processWrapper.in(ApprovalBusiness::getModuleId, approvalListReq.getModuleIdList());
            processWrapper.in(ApprovalBusiness::getSubjectId, approvalListReq.getSubjectIdList());
            // processWrapper.eq(ApprovalBusiness::getApprovalUids,
            // approvalListReq.getApplicantId());
            processWrapper.apply(
                    "FIND_IN_SET({0}, approval_uids)", approvalListReq.getApplicantId());
            processWrapper.in(ApprovalBusiness::getRecordId, approvalListReq.getRecordIdList());
            List<ApprovalBusiness> resultList = approvalBusinessMapper.selectList(processWrapper);
            respList =
                    resultList.stream()
                            .map(business -> convertMapResp(business, 2))
                            .collect(Collectors.toList());
        }
        if (approvalListReq.getApprovalType() == 3) {
            LambdaQueryWrapper<ApprovalBusiness> allWrapper = new LambdaQueryWrapper<>();
            allWrapper.in(ApprovalBusiness::getModuleId, approvalListReq.getModuleIdList());
            allWrapper.in(ApprovalBusiness::getSubjectId, approvalListReq.getSubjectIdList());
            allWrapper.in(ApprovalBusiness::getRecordId, approvalListReq.getRecordIdList());
            List<ApprovalBusiness> allList = approvalBusinessMapper.selectList(allWrapper);
            Set<Long> ids =
                    toDoList.stream().map(ApprovalBusiness::getId).collect(Collectors.toSet());
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

    // 查询单据审批操作日志---------------------------------------------------------------------
    @Override
    public List<ActionLogResp> getActionLog(ApprovalDataReq req) {
        Long subjectId = req.getSubjectId();
        Long recordId = req.getRecordId();
        Long moduleId = req.getModuleId();
        List<ActionLogResp> respList = new ArrayList<>();
        if (Objects.isNull(recordId) || Objects.isNull(moduleId) || Objects.isNull(subjectId)) {
            throw new BusinessException("subjectId、recordId和moduleId不能为空");
        }
        // 1. 查询业务主记录获取number
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(
                        new LambdaQueryWrapper<ApprovalVersionRecord>()
                                .eq(ApprovalVersionRecord::getRecordId, recordId)
                                .eq(ApprovalVersionRecord::getSubjectId, subjectId)
                                .eq(ApprovalVersionRecord::getModuleId, moduleId));
        if (Objects.isNull(approvalVersionRecord)) {
            return respList;
        }
        // 2. 构查询条件
        LambdaQueryWrapper<ApprovalActionLog> wrapper =
                new LambdaQueryWrapper<ApprovalActionLog>()
                        .eq(
                                ApprovalActionLog::getSerialNumber,
                                approvalVersionRecord.getSerialNumber())
                        .orderByAsc(ApprovalActionLog::getId);

        List<ApprovalActionLog> actionLogList = approvalActionLogMapper.selectList(wrapper);
        if (actionLogList.isEmpty()) {
            return respList;
        }
        // 3. 收集需要批量查询的ID
        for (ApprovalActionLog log : actionLogList) {
            ActionLogResp actionLogResp = new ActionLogResp();
            BeanUtils.copyProperties(log, actionLogResp);
            SysConfigItemResp modelConfig =
                    sysConfigCategoryApi.getConfigItemsByValue("logStateIcon", log.getActionIcon());
            actionLogResp.setActionTypeTitle(modelConfig.getLabel());
            UserDetailResp createByUser = sysUserApi.getUserDetailByUserId(log.getCreatedBy());
            if (createByUser != null) {
                ActionLogResp.UserVO userVO = new ActionLogResp.UserVO();
                userVO.setId(createByUser.getId());
                userVO.setUserName(createByUser.getUserName());
                userVO.setUserAvatar(createByUser.getUserAvatar());
                userVO.setDepartName(createByUser.getDepartName());
                userVO.setPositionName(createByUser.getPositionName());
                userVO.setWorkNumber(createByUser.getWorkNumber());
                actionLogResp.setCreatedUser(userVO);
            }
            UserDetailResp realUser = sysUserApi.getUserDetailByUserId(log.getRealUserId());
            if (realUser != null) {
                ActionLogResp.UserVO userVO = new ActionLogResp.UserVO();
                userVO.setId(realUser.getId());
                userVO.setUserName(realUser.getUserName());
                userVO.setUserAvatar(realUser.getUserAvatar());
                userVO.setDepartName(realUser.getDepartName());
                userVO.setPositionName(realUser.getPositionName());
                userVO.setWorkNumber(realUser.getWorkNumber());
                actionLogResp.setRealUser(userVO);
            }
            respList.add(actionLogResp);
        }
        return respList;
    }

    // 查询单据对应版本号和数据ID-----------------------------------------------------------------
    @Override
    public Map<String, Object> getApprovalChangeData(ApprovalDataReq req) {
        Long subjectId = req.getSubjectId();
        Long recordId = req.getRecordId();
        Long moduleId = req.getModuleId();
        if (Objects.isNull(recordId) || Objects.isNull(moduleId) || Objects.isNull(subjectId)) {
            throw new BusinessException("subjectId、recordId和moduleId不能为空");
        }
        // 1. 查询业务主记录获取number
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(
                        new LambdaQueryWrapper<ApprovalVersionRecord>()
                                .eq(ApprovalVersionRecord::getRecordId, recordId)
                                .eq(ApprovalVersionRecord::getSubjectId, subjectId)
                                .eq(ApprovalVersionRecord::getModuleId, moduleId));
        if ((Objects.isNull(approvalVersionRecord))) {
            return Map.of();
        }
        ApprovalVersionRecord oldApprovalVersionRecord =
                approvalVersionRecordMapper.selectById(approvalVersionRecord.getSourceId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentVersion", approvalVersionRecord.getVersionNo());
        result.put("sourceVersion", oldApprovalVersionRecord.getVersionNo());
        result.put("sourceRecordId", oldApprovalVersionRecord.getRecordId());
        if (approvalVersionRecord.getSerialNo() > 1) {
            ApprovalVersionRecord preApprovalVersionRecord =
                    approvalVersionRecordMapper.selectOne(
                            new LambdaQueryWrapper<ApprovalVersionRecord>()
                                    .eq(
                                            ApprovalVersionRecord::getSerialNumber,
                                            approvalVersionRecord.getSerialNumber())
                                    .eq(
                                            ApprovalVersionRecord::getSerialNo,
                                            approvalVersionRecord.getSerialNo() - 1));
            result.put("preRecordId", preApprovalVersionRecord.getRecordId());
        }
        return result;
    }

    // 查询审批步骤绑定的操作按钮------------------------------------------------------------------
    private List<Map<String, Object>> getOperationButton(ApprovalProcessReq approvalProcessReq) {
        Long userId = approvalProcessReq.getApplicantId();
        Long roleId = approvalProcessReq.getRoleId();
        LambdaQueryWrapper<ApprovalBusiness> businessWrapper = new LambdaQueryWrapper<>();
        businessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessReq.getModuleId());
        businessWrapper.eq(ApprovalBusiness::getRecordId, approvalProcessReq.getRecordId());
        ApprovalBusiness approvalChainBusiness = approvalBusinessMapper.selectOne(businessWrapper);
        List<Map<String, Object>> operations = new ArrayList<>();
        String btnType = approvalProcessReq.getButton();
        if (Objects.nonNull(btnType) && (btnType.equals("change") || btnType.equals("create"))) {
            operations.add(
                    showButtonInfo(
                            "submit",
                            null,
                            approvalChainBusiness.getSerialNumber(),
                            approvalChainBusiness.getSerialNo(),
                            approvalChainBusiness.getCurrentStep()));
            return operations;
        }
        if (Objects.isNull(approvalChainBusiness)) {
            return operations;
        }
        ApprovalProcessDTO approvalProcessDTO = convertApprovalDTO(approvalProcessReq);
        approvalProcessDTO.setSerialNumber(approvalChainBusiness.getSerialNumber());
        approvalProcessDTO.setSerialNo(approvalChainBusiness.getSerialNo());
        approvalProcessDTO.setCurrentStep(approvalChainBusiness.getCurrentStep());
        LambdaQueryWrapper<ApprovalChain> approvalWrapper = new LambdaQueryWrapper<>();
        approvalWrapper.eq(ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
        approvalWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
        approvalWrapper.eq(ApprovalChain::getCurrentStep, approvalProcessDTO.getCurrentStep());
        ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalWrapper);
        if (approvalChainBusiness.getStatusValue().equals(99)) {
            if (approvalChain.getCreatedBy().equals(userId) && checkRight(approvalProcessDTO)) {
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
        } else {
            if (Objects.isNull(approvalChain)) {
                LambdaQueryWrapper<ApprovalChain> approvalDeleteWrapper =
                        new LambdaQueryWrapper<>();
                approvalDeleteWrapper.eq(
                        ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
                approvalDeleteWrapper.eq(
                        ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
                approvalDeleteWrapper.eq(
                        ApprovalChain::getCurrentStep, approvalProcessDTO.getCurrentStep());
                ApprovalChain approvalChainDelete =
                        approvalChainMapper.selectOne(approvalDeleteWrapper);
                if (Objects.nonNull(approvalChainDelete)
                        && approvalChainDelete.getCreatedBy().equals(userId)
                        && checkRight(approvalProcessDTO)) {
                    operations.add(
                            showButtonInfo(
                                    "submit",
                                    null,
                                    approvalChainBusiness.getSerialNumber(),
                                    approvalChainBusiness.getSerialNo(),
                                    approvalChainBusiness.getCurrentStep()));
                    operations.add(
                            showButtonInfo(
                                    "cancel",
                                    null,
                                    approvalChainBusiness.getSerialNumber(),
                                    approvalChainBusiness.getSerialNo(),
                                    approvalChainBusiness.getCurrentStep()));
                }
            } else if ((Objects.nonNull(approvalChainBusiness.getApproverId())
                            && approvalChainBusiness.getApproverId().equals(userId))
                    || (Objects.nonNull(approvalChainBusiness.getApproveRoleId())
                            && approvalChainBusiness.getApproveRoleId().equals(roleId))) {
                LambdaQueryWrapper<ApprovalChainButton> approvalButtonWrapper =
                        new LambdaQueryWrapper<>();
                approvalButtonWrapper.eq(
                        ApprovalChainButton::getSerialNumber, approvalProcessDTO.getSerialNumber());
                approvalButtonWrapper.eq(
                        ApprovalChainButton::getSerialNo, approvalProcessDTO.getSerialNo());
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
            }
            if (Objects.nonNull(approvalChain)
                    && approvalChain.getCreatedBy().equals(userId)
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

    // 查询审批链全部审批步骤----------------------------------------------------------------------
    private List<Map<String, Object>> getApprovalStep(ApprovalProcessReq approvalProcessReq) {
        LambdaQueryWrapper<ApprovalVersionRecord> businessWrapper = new LambdaQueryWrapper<>();
        businessWrapper.eq(ApprovalVersionRecord::getModuleId, approvalProcessReq.getModuleId());
        businessWrapper.eq(ApprovalVersionRecord::getRecordId, approvalProcessReq.getRecordId());
        ApprovalVersionRecord approvalChainBusiness =
                approvalVersionRecordMapper.selectOne(businessWrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("module_id", 0);
        itemMap.put("is_end", 1);
        itemMap.put(
                "list",
                approvalChainStep(
                        approvalChainBusiness.getSerialNumber(),
                        approvalChainBusiness.getSerialNo(),
                        approvalProcessReq.getRoleId()));
        result.add(itemMap);
        return result;
    }

    // 获取版本列表，处理变更模式--------------------------------------------------------------
    protected List<Object> getVersionList(String getBusinessNumber, String btnType) {
        // 1. 获取数据
        LambdaQueryWrapper<ApprovalVersionRecord> wrapper =
                new LambdaQueryWrapper<ApprovalVersionRecord>()
                        .eq(ApprovalVersionRecord::getBusinessNumber, getBusinessNumber)
                        .select(
                                ApprovalVersionRecord::getId,
                                ApprovalVersionRecord::getVersionNo,
                                ApprovalVersionRecord::getStatusValue);
        List<ApprovalVersionRecord> versionDataList =
                approvalVersionRecordMapper.selectList(wrapper);

        // 2. 组装版本列表
        List<Map<String, Object>> versionList = new ArrayList<>();
        for (ApprovalVersionRecord item : versionDataList) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("record_id", item.getRecordId());
            map.put("version_no", item.getVersionNo());
            map.put("status_value", item.getStatusValue());
            map.put("btn_type", "change".equals(btnType) ? "change-view" : "");
            versionList.add(map);
        }

        int versionNo = 0;

        // 3. 处理 change 模式：追加一个新版本
        if ("change".equals(btnType) && CollectionUtil.isNotEmpty(versionList)) {
            // 过滤出 status_value = 99 的数据
            List<Map<String, Object>> sortData =
                    versionList.stream()
                            .filter(item -> Objects.equals(item.get("status_value"), 99))
                            .collect(Collectors.toList());

            if (CollectionUtil.isNotEmpty(sortData)) {
                // 按 id 倒序
                sortData.sort((a, b) -> Long.compare(((Long) b.get("id")), ((Long) a.get("id"))));
                // 取第一条
                Map<String, Object> lastData = new HashMap<>(sortData.get(0));
                lastData.put("id", 0);
                lastData.put("btn_type", "change-change");
                versionNo = versionList.size() + 1;
                lastData.put("version_no", versionNo);
                versionList.add(lastData);
            }
        }

        // 4. 按 version_no 倒序
        versionList.sort(
                (a, b) ->
                        Short.compare(
                                (Short.parseShort(b.get("version_no").toString())),
                                Short.parseShort(a.get("version_no").toString())));

        // 5. 返回 [versionList, versionNo]
        List<Object> result = new ArrayList<>();
        result.add(versionList);
        return result;
    }

    // 新建业务发起审批申请------------------------------------------------------------------------
    @Transactional
    public void businessCreate(ApprovalProcessDTO approvalProcessDTO) {
        // 生成唯一编号
        String businessNumber = ToolUtils.createNo("JD", 1, "", 0, null);
        String serialNumber = businessNumber + "@1";
        // 初始化审批链
        approvalProcessDTO.setSerialNumber(serialNumber);
        approvalProcessDTO.setSerialNo(1);
        initApprovalChainForModule(approvalProcessDTO);

        // 查询起始审批节点
        LambdaQueryWrapper<ApprovalChain> sysApprovalChainWrapper = new LambdaQueryWrapper<>();
        sysApprovalChainWrapper.eq(
                ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
        sysApprovalChainWrapper.eq(ApprovalChain::getCurrentStep, 1);
        ApprovalChain sysApprovalChain = approvalChainMapper.selectOne(sysApprovalChainWrapper);

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

        // 写入日志
        saveActionLog(0L, 0L, approvalProcessDTO);
    }

    // 变更业务发起审批申请------------------------------------------------------------------------
    @Transactional
    public void businessSubmit(ApprovalProcessDTO approvalProcessDTO) {
        LambdaQueryWrapper<ApprovalVersionRecord> approvalVersionWrapper =
                new LambdaQueryWrapper<>();
        approvalVersionWrapper.eq(
                ApprovalVersionRecord::getModuleId, approvalProcessDTO.getModuleId());
        approvalVersionWrapper
                .eq(ApprovalVersionRecord::getRecordId, approvalProcessDTO.getOldRecordId())
                .last("limit 1"); // 追加SQL limit
        ApprovalVersionRecord approvalRecord =
                approvalVersionRecordMapper.selectOne(approvalVersionWrapper);
        // 生成唯一编号
        int versionNo = approvalRecord.getVersionNo();
        if (approvalRecord.getStatusValue().equals(1)) {
            approvalProcessDTO.setSerialNumber(approvalRecord.getSerialNumber());
            approvalProcessDTO.setSerialNo(approvalRecord.getSerialNo() + 1);
        } else {
            versionNo = approvalRecord.getVersionNo() + 1;
            String serialNumber = approvalRecord.getBusinessNumber() + "@" + versionNo;
            approvalProcessDTO.setSerialNumber(serialNumber);
            approvalProcessDTO.setSerialNo(1);
        }
        // 初始化审批链
        initApprovalChainForModule(approvalProcessDTO);

        // 查询起始审批节点
        LambdaQueryWrapper<ApprovalChain> sysApprovalChainWrapper = new LambdaQueryWrapper<>();
        sysApprovalChainWrapper.eq(
                ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
        sysApprovalChainWrapper.eq(ApprovalChain::getCurrentStep, 1);
        ApprovalChain sysApprovalChain = approvalChainMapper.selectOne(sysApprovalChainWrapper);
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
        // 查询业务单据记录
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper
                .eq(ApprovalBusiness::getBusinessId, approvalProcessDTO.getBusinessId())
                .last("limit 1");
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);

        LambdaQueryWrapper<ApprovalVersionRecord> approvalSourceWrapper =
                new LambdaQueryWrapper<>();
        approvalSourceWrapper.eq(
                ApprovalVersionRecord::getSerialNumber, approvalBusiness.getSerialNumber());
        approvalSourceWrapper.eq(
                ApprovalVersionRecord::getSerialNo, approvalProcessDTO.getSerialNo());
        ApprovalVersionRecord approvalSource =
                approvalVersionRecordMapper.selectOne(approvalSourceWrapper);

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
        approvalVersionRecord.setSourceId(approvalSource.getId());
        approvalVersionRecord.setApprovalChainTypeId(sysApprovalChain.getApprovalChainTypeId());
        approvalVersionRecord.setCreatedBy(approvalProcessDTO.getApplicantId());
        approvalVersionRecord.setCreatedDate(LocalDateTime.now());
        approvalVersionRecord.setUpdatedBy(approvalProcessDTO.getApplicantId());
        approvalVersionRecord.setUpdatedDate(LocalDateTime.now());
        approvalVersionRecordMapper.insert(approvalVersionRecord);

        // 写入日志
        saveActionLog(0L, 0L, approvalProcessDTO);
    }

    // 审批通过---------------------------------------------------------------------
    @Transactional
    public Integer businessApprove(ApprovalProcessDTO approvalProcessDTO) {
        // 查询当前业务单据记录
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper
                .eq(ApprovalBusiness::getBusinessId, approvalProcessDTO.getBusinessId())
                .last("limit 1");
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);
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
        approvalChainWrapper.eq(ApprovalChain::getCurrentStep, approvalProcessDTO.getCurrentStep());
        ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalChainWrapper);
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
        approvalChain.setApproved(true);
        approvalChain.setApprovalUids(String.join(",", approvalUidList));
        approvalBusiness.setApprovalUids(String.join(",", approvalUidList));
        approvalChainMapper.updateById(approvalChain);
        // 判断当前步骤是否审批通过
        approvalRule(approvalProcessDTO, approvalChain);
        boolean approved = true;
        if (List.of("all_members_done", "percent_of_group")
                .contains(approvalChain.getApprovalRule())) {
            int percent =
                    "all_members_done".equals(approvalChain.getApprovalRule())
                            ? 100
                            : approvalChain.getRoleApprovalPercent();
            long currentApproverIdNumber =
                    IntStream.range(0, approvalChain.getApproverIdList().length())
                                    .filter(i -> approvalChain.getApproverIdList().charAt(i) == '，')
                                    .count()
                            + 2;
            int allApproverIdNumber =
                    sysUserApi.getUsersByRoleId(approvalChain.getApproveRoleId()).size();
            if (currentApproverIdNumber / allApproverIdNumber < percent / 100) {
                approved = false;
            }
        }
        if (approved) {
            LambdaQueryWrapper<ApprovalChain> approvalNextWrapper = new LambdaQueryWrapper<>();
            approvalNextWrapper.eq(
                    ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
            approvalNextWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
            approvalNextWrapper.eq(ApprovalChain::getCurrentStep, approvalChain.getNextStep());
            ApprovalChain approvalChainNext = approvalChainMapper.selectOne(approvalNextWrapper);
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
            LambdaQueryWrapper<ApprovalVersionRecord> approvalRecordWrapper =
                    new LambdaQueryWrapper<>();
            approvalRecordWrapper.eq(
                    ApprovalVersionRecord::getSerialNumber, approvalProcessDTO.getSerialNumber());
            approvalRecordWrapper
                    .eq(ApprovalVersionRecord::getSerialNo, approvalProcessDTO.getSerialNo())
                    .last("limit 1");
            ApprovalVersionRecord approvalRecord =
                    approvalVersionRecordMapper.selectOne(approvalRecordWrapper);
            approvalRecord.setCurrentStep(approvalBusiness.getCurrentStep());
            approvalRecord.setStatusId(approvalBusiness.getStatusId());
            approvalRecord.setStatusTitle(approvalChainStatus.getTitle());
            approvalRecord.setStatusBackground(approvalChainStatus.getStatusBackground());
            approvalRecord.setStatusFontColor(approvalChainStatus.getStatusFontColor());
            approvalRecord.setStatusValue(approvalChainStatus.getStatusValue());
            approvalVersionRecordMapper.updateById(approvalRecord);
        }
        // 更新业务主记录
        approvalBusinessMapper.updateById(approvalBusiness);
        // 写入日志
        saveActionLog(
                approvalChain.getCurrentStatusGroupId(),
                approvalChain.getCurrentStatusChildId(),
                approvalProcessDTO);

        return approvalBusiness.getStatusValue();
    }

    // 审批驳回------------------------------------------------------------------
    @Transactional
    public void businessReject(ApprovalProcessDTO approvalProcessDTO) {
        // 查询业务记录
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper.eq(ApprovalBusiness::getRecordId, approvalProcessDTO.getRecordId());
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);
        approvalProcessDTO.setSerialNo(approvalBusiness.getSerialNo());
        approvalProcessDTO.setSerialNumber(approvalBusiness.getSerialNumber());

        LambdaQueryWrapper<ApprovalVersionRecord> approvalVersionRecordWrapper =
                new LambdaQueryWrapper<>();
        approvalVersionRecordWrapper.eq(
                ApprovalVersionRecord::getSerialNumber, approvalBusiness.getSerialNumber());
        approvalVersionRecordWrapper.eq(
                ApprovalVersionRecord::getSerialNo, approvalBusiness.getSerialNo());
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(approvalVersionRecordWrapper);

        LambdaQueryWrapper<ApprovalChain> approvalChainWrapper = new LambdaQueryWrapper<>();
        approvalChainWrapper.eq(ApprovalChain::getSerialNumber, approvalBusiness.getSerialNumber());
        approvalChainWrapper.eq(ApprovalChain::getSerialNo, approvalBusiness.getSerialNo());
        approvalChainWrapper.eq(ApprovalChain::getSkipped, false);
        approvalChainWrapper.eq(ApprovalChain::getApproved, false);
        approvalChainWrapper.eq(ApprovalChain::getCurrentStep, approvalBusiness.getCurrentStep());
        ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalChainWrapper);
        approvalRule(approvalProcessDTO, approvalChain);
        ApprovalChain businessApprovalChain;
        if (approvalBusiness.getCurrentStep().equals(1)
                || approvalChain.getRejectRule().equals("rejectStart")) {
            approvalChain.setReason(approvalProcessDTO.getReason());
            businessApprovalChain = approvalChain;
            approvalBusiness.setCurrentStep(1);
            approvalVersionRecord.setCurrentStep(1);
            ModuleApprovalChainConfigResp.StatusNode statusNode =
                    sysStatusApi.getStatusByValue(1, approvalProcessDTO.getModuleId());
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
                                    .eq(ApprovalChain::getCurrentStep, specifyStep));

            businessApprovalChain = backConfig;
            LambdaUpdateWrapper<ApprovalChain> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
            updateWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
            updateWrapper.between(
                    ApprovalChain::getCurrentStep, specifyStep, approvalBusiness.getCurrentStep());
            updateWrapper.set(ApprovalChain::getApproved, false);
            updateWrapper.set(ApprovalChain::getApproverIdList, null);
            updateWrapper.set(ApprovalChain::getApprovalUids, null);
            updateWrapper.set(ApprovalChain::getApprovalUid, null);
            approvalChainMapper.update(updateWrapper);

            approvalBusiness.setCurrentStep(approvalChain.getUpStep());
            approvalVersionRecord.setCurrentStep(approvalChain.getUpStep());
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
        }
        approvalBusiness.setApproverId(businessApprovalChain.getApproverId());
        approvalBusiness.setApproveRoleId(businessApprovalChain.getApproveRoleId());
        approvalBusiness.setApprovalUids(
                String.join(",", approvalProcessDTO.getApplicantId().toString()));
        approvalBusinessMapper.updateById(approvalBusiness);
        approvalVersionRecordMapper.updateById(approvalVersionRecord);
        // 写入日志
        saveActionLog(
                approvalChain.getCurrentStatusGroupId(),
                approvalChain.getCurrentStatusChildId(),
                approvalProcessDTO);
    }

    // 撤销申请-------------------------------------------------------------------------
    @Transactional
    public void businessRevoke(ApprovalProcessDTO approvalProcessDTO) {
        LambdaQueryWrapper<ApprovalBusiness> approvalBusinessWrapper = new LambdaQueryWrapper<>();
        approvalBusinessWrapper.eq(ApprovalBusiness::getModuleId, approvalProcessDTO.getModuleId());
        approvalBusinessWrapper.eq(ApprovalBusiness::getRecordId, approvalProcessDTO.getRecordId());
        ApprovalBusiness approvalBusiness =
                approvalBusinessMapper.selectOne(approvalBusinessWrapper);
        approvalProcessDTO.setSerialNo(approvalBusiness.getSerialNo());
        approvalProcessDTO.setSerialNumber(approvalBusiness.getSerialNumber());
        LambdaQueryWrapper<ApprovalVersionRecord> approvalRecordWrapper =
                new LambdaQueryWrapper<>();
        approvalRecordWrapper.eq(
                ApprovalVersionRecord::getSerialNumber, approvalBusiness.getSerialNumber());
        approvalRecordWrapper.eq(
                ApprovalVersionRecord::getSerialNo, approvalBusiness.getSerialNo());
        ApprovalVersionRecord approvalVersionRecord =
                approvalVersionRecordMapper.selectOne(approvalRecordWrapper);
        approvalVersionRecord.setStatusValue(-99);
        approvalVersionRecordMapper.updateById(approvalVersionRecord);
        ApprovalVersionRecord oldApprovalVersionRecord =
                approvalVersionRecordMapper.selectById(approvalVersionRecord.getSourceId());
        LambdaQueryWrapper<ApprovalChain> ApprovalChainWrapper = new LambdaQueryWrapper<>();
        ApprovalChainWrapper.eq(
                ApprovalChain::getSerialNumber, oldApprovalVersionRecord.getSerialNumber());
        ApprovalChainWrapper.eq(ApprovalChain::getSerialNo, oldApprovalVersionRecord.getSerialNo());
        ApprovalChainWrapper.eq(
                ApprovalChain::getCurrentStep, oldApprovalVersionRecord.getCurrentStep());
        ApprovalChain approvalChain = approvalChainMapper.selectOne(ApprovalChainWrapper);
        ModuleApprovalChainConfigResp.StatusNode statusNode =
                sysStatusApi.getStatusByValue(-99, approvalProcessDTO.getModuleId());
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
        approvalVersionRecord.setReason(approvalProcessDTO.getReason());
        approvalVersionRecord.setStatusTitle(statusNode.getTitle());
        approvalVersionRecord.setStatusId(statusNode.getId());
        approvalVersionRecord.setStatusValue(statusNode.getStatusValue());
        approvalVersionRecord.setStatusBackground(statusNode.getStatusBackground());
        approvalVersionRecord.setStatusFontColor(statusNode.getStatusFontColor());
        approvalVersionRecordMapper.updateById(approvalVersionRecord);

        // 写入日志
        saveActionLog(
                approvalChain.getCurrentStatusGroupId(),
                approvalChain.getCurrentStatusChildId(),
                approvalProcessDTO);
    }

    // 保存操作日志-----------------------------------------------------------------------
    @Transactional
    public void saveActionLog(
            Long oldStatusGroupId, Long oldStatusId, ApprovalProcessDTO approvalProcessDTO) {
        // 1. 获取对应节点 action 的操作日志描述
        String actionName = "";
        if (List.of("cancel", "submit", "create").contains(approvalProcessDTO.getButton())) {
            SysConfigItemResp modelConfig =
                    sysConfigCategoryApi.getConfigItemsByValue(
                            "logStateIcon", approvalProcessDTO.getButton());
            if (Objects.nonNull(modelConfig) && Objects.nonNull(modelConfig.getLabel())) {
                actionName = "已" + modelConfig.getLabel();
            }
        } else {
            LambdaQueryWrapper<ApprovalChainButton> configWrapper = new LambdaQueryWrapper<>();
            configWrapper.eq(
                    ApprovalChainButton::getSerialNumber, approvalProcessDTO.getSerialNumber());
            configWrapper.eq(ApprovalChainButton::getSerialNo, approvalProcessDTO.getSerialNo());
            configWrapper.eq(
                    ApprovalChainButton::getCurrentStep, approvalProcessDTO.getCurrentStep());
            configWrapper
                    .eq(ApprovalChainButton::getIcon, approvalProcessDTO.getButton())
                    .last("LIMIT 1");
            ApprovalChainButton approvalChainButton =
                    approvalChainButtonMapper.selectOne(configWrapper);
            if (Objects.nonNull(approvalChainButton)) {
                actionName = approvalChainButton.getLogName();
            }
        }

        // 2. 构建日志实体
        LocalDateTime time = LocalDateTime.now();
        ApprovalActionLog logEntity = new ApprovalActionLog();
        logEntity.setActionType(approvalProcessDTO.getButton());
        logEntity.setActionIcon(approvalProcessDTO.getButton());
        logEntity.setActionName(actionName);
        logEntity.setSerialNumber(approvalProcessDTO.getSerialNumber());
        logEntity.setBusinessId(approvalProcessDTO.getRecordId());
        logEntity.setModuleId(approvalProcessDTO.getModuleId());
        logEntity.setStatusGroupId(oldStatusGroupId);
        logEntity.setStatusId(oldStatusId);
        logEntity.setDescription(approvalProcessDTO.getReason());
        logEntity.setRealUserId(approvalProcessDTO.getCurrentUserId());
        logEntity.setCreatedBy(approvalProcessDTO.getApplicantId());
        logEntity.setCreatedDate(time);
        approvalActionLogMapper.insert(logEntity);
    }

    // =========================辅助方法===============================================
    // 获取业务流审批步骤列表------------------------------------------------------------
    private List<Map<String, Object>> approvalChainStep(
            String serialNumber, int serialNo, Long roleId) {
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
            if (roleId.equals(Long.parseLong(item.getApproveRoleId().toString()))) {
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
            map.put("status_background", status == null ? "" : status.getStatusBackground());
            map.put("status_font_color", status == null ? "" : status.getStatusFontColor());
            map.put("status_value", status == null ? "" : status.getStatusValue());
            map.put("is_approved", item.getApproved());
            map.put("is_highlight", isHighlight);
            map.put("is_operate", isOperate);
            map.put("can_do", (isHighlight == 1 && isOperate == 1) ? 1 : 0);
            map.put("is_collaboration", item.getCollaborated());
            map.put("approval_rule", item.getApprovalRule());
            data.add(map);

            // 注释代码完全保留（和PHP一致）
            /*
            if (isHighlight == 1) {
                List<String> ruleList = Arrays.asList("all_data_upload_done_role_group", ...);
                if (ruleList.contains(item.getApprovalRule())) {
                    if (index > 0 && data.get(index-1).get("is_operate").equals(1)) {
                        data.get(index-1).put("can_do", 1);
                    }
                }
            }
            */

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
                    throw new BusinessException(400, item.getNextStatusChildId() + "此状态已删除.");
                }

                int lastHighlight = 0;
                if (isEnd == 1 && item.getApproved()) {
                    lastHighlight = 1;
                }

                Map<String, Object> lastMap = new HashMap<>();
                lastMap.put("id", model.getId());
                lastMap.put("order", x + 1);
                lastMap.put("title", model.getTitle());
                lastMap.put("status_background", model.getStatusBackground());
                lastMap.put("status_font_color", model.getStatusFontColor());
                lastMap.put("status_value", model.getStatusValue());
                lastMap.put("is_approved", item.getApproved());
                lastMap.put("is_highlight", lastHighlight);
                lastMap.put("can_do", 0);
                lastMap.put("is_collaboration", item.getCollaborated());
                lastMap.put("approval_rule", item.getApprovalRule());
                data.add(lastMap);
            }
            x++;
        }

        return data;
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
            modelConfig = sysConfigCategoryApi.getConfigItemsByValue("ButtonStyle", alias);
            item.put("key", alias);
            item.put("title", modelConfigButton.getTitle());
            item.put("show_title", modelConfigButton.getTitle());
            Map<String, Object> property = new HashMap<>();
            property.put("default_bg_color", modelConfigButton.getDefaultBgColor());
            property.put("default_font_color", modelConfigButton.getDefaultFontColor());
            property.put("levitate_bg_color", modelConfigButton.getLevitateBgColor());
            property.put("levitate_font_color", modelConfigButton.getLevitateFontColor());
            property.put("selected_bg_color", modelConfigButton.getSelectedBgColor());
            property.put("selected_font_color", modelConfigButton.getSelectedFontColor());
            item.put("extra_property", property);
            item.put("action_icon", approvalButton.getIcon());
            item.put("action_name", approvalButton.getLogName());
            if (alias.equals("reject")) {
                // 查询当前审批步骤
                ApprovalChain approvalChain =
                        approvalChainMapper.selectOne(
                                new LambdaQueryWrapper<ApprovalChain>()
                                        .eq(ApprovalChain::getSerialNumber, getSerialNumber)
                                        .eq(ApprovalChain::getSerialNo, getSerialNo)
                                        .eq(ApprovalChain::getCurrentStep, currentStep)
                                        .last("LIMIT 1"));
                item.put("reject_rule", approvalChain.getRejectRule());
            }
        } else {
            modelConfig = sysConfigCategoryApi.getConfigItemsByValue("ButtonStyle", alias);
            if (Objects.isNull(modelConfig)) {
                return null;
            }
            item.put("key", alias);
            item.put("title", modelConfig.getLabel());
            item.put("show_title", modelConfig.getLabel());
            String extraPropertyStr = modelConfig.getExtra();
            if (Objects.nonNull(extraPropertyStr) && !extraPropertyStr.isEmpty()) {
                Type type = new TypeReference<Map<String, Object>>() {};
                Map<String, Object> extraProperty =
                        BeanUtil.beanToMap(JSONUtil.toBean(extraPropertyStr, type, true));
                Map<String, Object> property = new HashMap<>();
                property.put("default_bg_color", MapUtil.getStr(extraProperty, "default_bg_color"));
                property.put(
                        "default_font_color", MapUtil.getStr(extraProperty, "default_font_color"));
                property.put(
                        "levitate_bg_color", MapUtil.getStr(extraProperty, "levitate_bg_color"));
                property.put(
                        "levitate_font_color",
                        MapUtil.getStr(extraProperty, "levitate_font_color"));
                property.put(
                        "selected_bg_color", MapUtil.getStr(extraProperty, "selected_bg_color"));
                property.put(
                        "selected_font_color",
                        MapUtil.getStr(extraProperty, "selected_font_color"));
                item.put("extra_property", property);
            } else {
                Map<String, Object> property = new HashMap<>();
                property.put("default_bg_color", "");
                property.put("default_font_color", "");
                property.put("levitate_bg_color", "");
                property.put("levitate_font_color", "");
                property.put("selected_bg_color", "");
                property.put("selected_font_color", "");
                item.put("extra_property", property);
            }
            item.put("action_icon", modelConfig.getValue());
            item.put("action_name", "已" + modelConfig.getLabel());
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
        Long userId = approvalProcessDTO.getCurrentUserId();
        // 1. 查询审批链配置列表
        ModuleApprovalChainConfigResp approvalConfig =
                sysApprovalChainConfigApi.getApprovalConfig(approvalProcessDTO.getModuleId());
        if (approvalConfig.getApprovalChainConfigList().isEmpty())
            throw new BusinessException("未找到审批链配置");
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

    // ApprovalProcessReq转换为ApprovalProcessDTO
    private ApprovalProcessDTO convertApprovalDTO(ApprovalProcessReq approvalProcessReq) {
        ApprovalProcessDTO approvalProcessDTO = new ApprovalProcessDTO();
        BeanUtils.copyProperties(approvalProcessReq, approvalProcessDTO);
        return approvalProcessDTO;
    }

    private Map<String, Object> convertMapResp(
            ApprovalBusiness approvalBusiness, Integer approvalType) {
        Map<String, Object> approvalListResp = new HashMap<>();
        Map<String, Object> businessNo = new HashMap<>();
        businessNo.put("business_number", approvalBusiness.getBusinessNumber());
        businessNo.put("version_no", approvalBusiness.getVersionNo());
        approvalListResp.put("businessNo", businessNo);
        UserDetailResp createByUser =
                sysUserApi.getUserDetailByUserId(approvalBusiness.getCreatedBy());
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("created_by_name", createByUser.getUserName());
        userInfo.put("u_avatar", createByUser.getUserAvatar());
        userInfo.put("u_work_number", createByUser.getWorkNumber());
        approvalListResp.put("user", userInfo);
        approvalListResp.put("createdDate", approvalBusiness.getCreatedDate());
        Map<String, Object> approvalStatus = new HashMap<>();
        approvalStatus.put("id", approvalBusiness.getStatusId());
        approvalStatus.put("status_value", approvalBusiness.getStatusValue());
        approvalStatus.put("title", approvalBusiness.getStatusTitle());
        approvalStatus.put("status_background", approvalBusiness.getStatusBackground());
        approvalStatus.put("status_font_color", approvalBusiness.getStatusFontColor());
        approvalListResp.put("approvalStatus", approvalStatus);
        Map<String, Object> btnInfo = new LinkedHashMap<>();
        String alias = "view";
        String btnName = "查阅";
        if (approvalType == 1) {
            alias = "handle";
            btnName = "办理";
        }
        btnInfo.put("buttonKey", alias);
        SysConfigItemResp modelConfig =
                sysConfigCategoryApi.getConfigItemsByValue("ListButtonStyle", alias);
        if (Objects.nonNull(modelConfig) && Objects.nonNull(modelConfig.getLabel())) {
            btnInfo.put("buttonName", modelConfig.getLabel());
        } else {
            btnInfo.put("buttonName", btnName);
        }
        List<Map<String, Object>> btnList = new ArrayList<>();
        btnList.add(btnInfo);
        approvalListResp.put("btnList", btnList);
        return approvalListResp;
    }
}
