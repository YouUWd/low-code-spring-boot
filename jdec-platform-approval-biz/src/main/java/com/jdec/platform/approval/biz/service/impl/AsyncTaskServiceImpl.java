package com.jdec.platform.approval.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.approval.api.AsyncTaskService;
import com.jdec.platform.approval.api.dto.ApprovalProcessDTO;
import com.jdec.platform.approval.biz.dto.LogContentDTO;
import com.jdec.platform.approval.biz.entity.ApprovalChain;
import com.jdec.platform.approval.biz.mapper.ApprovalChainButtonMapper;
import com.jdec.platform.approval.biz.mapper.ApprovalChainMapper;
import com.jdec.platform.approval.biz.util.JsonUtil;
import com.jdec.platform.approval.biz.util.ToolUtils;
import com.jdec.platform.common.api.MonitorLogApi;
import com.jdec.platform.common.api.dto.log.*;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.config.api.*;
import com.jdec.platform.config.api.dto.request.SendWechatMessageReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.hr.api.bo.SubjectBO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.RequestContextSnapshot;
import com.jdec.platform.shared.context.RequestContextSnapshotHolder;
import com.jdec.platform.shared.datasource.DataSourceContextHolder;
import com.jdec.platform.shared.datasource.ProjectProperties;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步任务服务实现（独立 Bean，专门处理异步操作）
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>所有方法都加 @Async，明确声明为异步
 *   <li>每个方法都接收 projectNo 参数，用于动态切换数据源
 *   <li>需要事务的方法使用 @Transactional 并指定事务管理器
 * </ul>
 *
 * @author JDEC Platform Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskServiceImpl implements AsyncTaskService {

    private final ApprovalChainButtonMapper approvalChainButtonMapper;
    private final SysConfigCategoryApi sysConfigCategoryApi;
    private final SubjectApi subjectApi;
    private final SysMenuApi sysMenuApi;
    private final SysModuleApi sysModuleApi;
    private final MonitorLogApi monitorLogApi;
    private final SysUserApi sysUserApi;
    private final SysWechatTemplateApi sysWechatTemplateApi;
    private final ApprovalChainMapper approvalChainMapper;
    private final ProjectProperties projectProperties;

    // ==================== 企业微信消息（无事务，纯 IO） ====================
    @Override
    @Async("taskAsyncExecutor") // 指定专用线程池
    public void sendWechatMessageAsync(
            String projectNo,
            ApprovalProcessDTO approvalProcessDTO,
            Integer msgType,
            String triggerType,
            Long templateId) {
        log.info(
                "【异步任务】开始发送企微消息, projectNo={}, dataId={}",
                projectNo,
                approvalProcessDTO.getRecordId());
        AppContext.setContext(projectNo, approvalProcessDTO.getSubjectId());
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        boolean switched = false;
        if (dataSourceKey != null && !dataSourceKey.isBlank()) {
            DataSourceContextHolder.set(dataSourceKey);
            switched = true;
            log.debug("异步线程切换数据源至：{} (项目：{})", dataSourceKey, projectNo);
        }
        try {
            if (msgType.equals(1)) {
                sendWechatMessage(triggerType, approvalProcessDTO);
            } else if (msgType.equals(2)) {
                sendWechatMessageById(templateId, approvalProcessDTO);
            }
        } finally {
            if (switched) {
                DataSourceContextHolder.clear(); // 弹出自己压入的
            }
        }
    }

    // ==================== 审计日志（需要事务，跨数据源） ====================
    @Override
    @Async("taskAsyncExecutor")
    public void saveAuditLogAsync(String projectNo, ApprovalProcessDTO approvalProcessDTO) {
        log.info(
                "【异步任务】开始记录审计日志, projectNo={}, dataId={}",
                projectNo,
                approvalProcessDTO.getRecordId());
        AppContext.setContext(projectNo, approvalProcessDTO.getSubjectId());
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        boolean switched = false;
        if (dataSourceKey != null && !dataSourceKey.isBlank()) {
            DataSourceContextHolder.set(dataSourceKey);
            switched = true;
            log.debug("异步线程切换数据源至：{} (项目：{})", dataSourceKey, projectNo);
        }
        try {
            saveActionLog(approvalProcessDTO);
        } finally {
            if (switched) {
                DataSourceContextHolder.clear(); // 弹出自己压入的
            }
        }
    }

    // =========================================================================
    // 保存操作日志-----------------------------------------------------------------------
    private void saveActionLog(ApprovalProcessDTO approvalProcessDTO) {
        try {
            // 日志记录
            MonitorLogSaveReq monitorControlLogReq = new MonitorLogSaveReq();
            monitorControlLogReq.setType(0);
            monitorControlLogReq.setSysType("operationLog");
            monitorControlLogReq.setSubjectId(approvalProcessDTO.getSubjectId());

            MonitorLoginInfo loginInfo = new MonitorLoginInfo();
            loginInfo.setUserId(approvalProcessDTO.getCurrentUserId());
            loginInfo.setRoleId(approvalProcessDTO.getRoleId());
            monitorControlLogReq.setLoginInfo(loginInfo);
            boolean mockFlag =
                    approvalProcessDTO.getCurrentUserId() != null
                            && approvalProcessDTO.getApplicantId() != null
                            && !approvalProcessDTO
                                    .getCurrentUserId()
                                    .equals(approvalProcessDTO.getApplicantId());
            MonitorMockInfo mockInfo = new MonitorMockInfo();
            mockInfo.setFlag(mockFlag ? 1 : 0);
            mockInfo.setUserId(approvalProcessDTO.getApplicantId());
            monitorControlLogReq.setMockInfo(mockInfo);

            SysConfigItemResp modelConfig =
                    sysConfigCategoryApi.getConfigItemsByValue(
                            "logStateIcon", approvalProcessDTO.getButton());
            MonitorApproveOperation approveBase = new MonitorApproveOperation();
            approveBase.setLogSubType("operationLog");
            approveBase.setPath(modelConfig.getExtra());
            approveBase.setName(modelConfig.getLabel());
            approveBase.setResult(modelConfig.getDescription());
            approveBase.setType(approvalProcessDTO.getCurrentUserId().equals(9999999L) ? 0 : 1);
            monitorControlLogReq.setApprove(approveBase);

            RequestContextSnapshot requestContext = RequestContextSnapshotHolder.get();
            monitorControlLogReq.setIp(requestContext.getClientIp());
            monitorControlLogReq.setBrowser(requestContext.getBrowser());
            monitorControlLogReq.setPlatForm(requestContext.getPlatform());
            monitorControlLogReq.setModuleId(approvalProcessDTO.getModuleId());
            monitorControlLogReq.setDataId(approvalProcessDTO.getRecordId());

            SysModuleResp module =
                    sysModuleApi.getModuleCompleteById(
                            AppContext.getProjectNo(),
                            approvalProcessDTO.getSubjectId(),
                            approvalProcessDTO.getModuleId());
            MonitorContentOperation contentBase = new MonitorContentOperation();
            contentBase.setLogSubType("operationLog");
            contentBase.setModuleName(module.getModule().getModuleName());
            contentBase.setVersion(approvalProcessDTO.getVersionNo());
            contentBase.setLinkName(approvalProcessDTO.getBusinessNumber());
            contentBase.setOperation(modelConfig.getLabel());
            contentBase.setResult("成功");
            contentBase.setFontColor("");
            monitorControlLogReq.setContent(contentBase);
            monitorControlLogReq.setRemark(approvalProcessDTO.getReason());
            monitorLogApi.saveLog(monitorControlLogReq);
            log.info(
                    "审批操作日志保存成功: businessNumber={}, businessId={}, action={}",
                    approvalProcessDTO.getBusinessNumber(),
                    approvalProcessDTO.getRecordId(),
                    approvalProcessDTO.getButton());

        } catch (Exception e) {
            // 日志异常
            log.error("审批操作日志保存异常", e);
        }
    }

    // 保存消息日志-----------------------------------------------------------------------
    private void saveWeChatMessageLog(
            ApprovalProcessDTO approvalProcessDTO, List<Long> receivers, String content) {
        try {
            // 插入企业微信消息日志记录
            MonitorLogSaveReq monitorMessageLogReq = new MonitorLogSaveReq();
            monitorMessageLogReq.setType(1);
            monitorMessageLogReq.setSysType("messageLog");
            monitorMessageLogReq.setSubjectId(approvalProcessDTO.getSubjectId());

            MonitorLoginInfo loginInfo = new MonitorLoginInfo();
            loginInfo.setUserId(approvalProcessDTO.getCurrentUserId());
            loginInfo.setRoleId(approvalProcessDTO.getRoleId());
            monitorMessageLogReq.setLoginInfo(loginInfo);
            boolean mockFlag =
                    approvalProcessDTO.getCurrentUserId() != null
                            && approvalProcessDTO.getApplicantId() != null
                            && !approvalProcessDTO
                                    .getCurrentUserId()
                                    .equals(approvalProcessDTO.getApplicantId());
            MonitorMockInfo mockInfo = new MonitorMockInfo();
            mockInfo.setFlag(mockFlag ? 1 : 0);
            mockInfo.setUserId(approvalProcessDTO.getApplicantId());
            monitorMessageLogReq.setMockInfo(mockInfo);

            SysConfigItemResp modelConfig =
                    sysConfigCategoryApi.getConfigItemsByValue(
                            "logStateIcon", approvalProcessDTO.getButton());

            RequestContextSnapshot requestContext = RequestContextSnapshotHolder.get();
            monitorMessageLogReq.setIp(requestContext.getClientIp());
            monitorMessageLogReq.setBrowser(requestContext.getBrowser());
            monitorMessageLogReq.setPlatForm(requestContext.getPlatform());
            monitorMessageLogReq.setModuleId(approvalProcessDTO.getModuleId());
            monitorMessageLogReq.setDataId(approvalProcessDTO.getRecordId());

            SysModuleResp module =
                    sysModuleApi.getModuleCompleteById(
                            AppContext.getProjectNo(),
                            approvalProcessDTO.getSubjectId(),
                            approvalProcessDTO.getModuleId());
            MonitorContentMessage contentBase = new MonitorContentMessage();
            contentBase.setLogSubType("messageLog");
            contentBase.setModuleName(module.getModule().getModuleName());
            contentBase.setVersion(approvalProcessDTO.getVersionNo());
            contentBase.setLinkName(approvalProcessDTO.getBusinessNumber());
            contentBase.setOperation(modelConfig.getLabel());
            contentBase.setContent(content);
            monitorMessageLogReq.setContent(contentBase);

            if (Objects.nonNull(approvalProcessDTO.getMenuId())) {
                SysMenuResp menu = sysMenuApi.getMenuById(approvalProcessDTO.getMenuId());
                LogContentDTO contentDTO =
                        LogContentDTO.builder()
                                .moduleId(monitorMessageLogReq.getModuleId())
                                .moduleName(module.getModule().getModuleName())
                                .menuId(approvalProcessDTO.getMenuId())
                                .menuParam(menu.getParam())
                                .hrefName(approvalProcessDTO.getBusinessNumber())
                                .dataId(String.valueOf(monitorMessageLogReq.getDataId()))
                                .orderVersion(approvalProcessDTO.getVersionNo().toString())
                                .controlName(modelConfig.getLabel())
                                .messageReceivers(receivers)
                                .build();
                monitorMessageLogReq.setRemark(JsonUtil.obj2Json(contentDTO));
            }
            monitorLogApi.saveLog(monitorMessageLogReq);
            log.info(
                    "消息日志保存成功: businessNumber={}, businessId={}, action={}",
                    approvalProcessDTO.getBusinessNumber(),
                    approvalProcessDTO.getRecordId(),
                    approvalProcessDTO.getButton());
        } catch (Exception e) {
            // 日志异常
            log.error("消息日志保存异常", e);
        }
    }

    // ======================================组装并发送审批消息==========================================
    // 根据模板ID发送消息-----------------------------------
    private void sendWechatMessageById(Long templateId, ApprovalProcessDTO approvalProcessDTO) {
        WechatTemplateEntryResp template = sysWechatTemplateApi.getTemplateById(templateId);
        String content = formatWechatMessage(template, approvalProcessDTO);
        List<String> userIds =
                getSendUserList(approvalProcessDTO, templateId).stream()
                        .map(String::valueOf)
                        .toList();
        SendWechatMessageReq sendWechatMessageReq = new SendWechatMessageReq();
        sendWechatMessageReq.setMessageTitle(template.getTemplateTitle());
        sendWechatMessageReq.setMessageType("markdown");
        sendWechatMessageReq.setMessageContent(content);
        sendWechatMessageReq.setReceivers(userIds);
        sysWechatTemplateApi.sendWechatMessage(
                AppContext.getProjectNo(), approvalProcessDTO.getSubjectId(), sendWechatMessageReq);
        saveWeChatMessageLog(
                approvalProcessDTO, userIds.stream().map(Long::valueOf).toList(), content);
    }

    // 发送提交和取消消息-----------------------------------
    private void sendWechatMessage(String triggerType, ApprovalProcessDTO approvalProcessDTO) {
        String projectNo = AppContext.getProjectNo();
        WechatTemplateEntryResp template =
                sysWechatTemplateApi.getTemplateByModuleId(
                        projectNo,
                        approvalProcessDTO.getSubjectId(),
                        approvalProcessDTO.getModuleId(),
                        triggerType);
        String content = formatWechatMessage(template, approvalProcessDTO);
        List<String> userIds =
                getSendUserList(approvalProcessDTO, template.getId()).stream()
                        .map(String::valueOf)
                        .toList();
        SendWechatMessageReq sendWechatMessageReq = new SendWechatMessageReq();
        sendWechatMessageReq.setMessageTitle(template.getTemplateTitle());
        sendWechatMessageReq.setMessageType("markdown");
        sendWechatMessageReq.setMessageContent(content);
        sendWechatMessageReq.setReceivers(userIds);
        sysWechatTemplateApi.sendWechatMessage(
                AppContext.getProjectNo(), approvalProcessDTO.getSubjectId(), sendWechatMessageReq);
        saveWeChatMessageLog(
                approvalProcessDTO, userIds.stream().map(Long::valueOf).toList(), content);
    }

    // 组装消息
    private String formatWechatMessage(
            WechatTemplateEntryResp template, ApprovalProcessDTO approvalProcessDTO) {
        try {
            // 1. 转换对象
            Map<String, Object> sourceMap = BeanUtil.beanToMap(approvalProcessDTO, true, true);
            // 2. 获取参数替换映射
            Map<String, String> params =
                    getParametersByTemplate(approvalProcessDTO, template, sourceMap);
            // 3. 替换模板内容中的占位符
            String content = template.getTemplateContent();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String placeholder = entry.getKey();
                String value = entry.getValue();
                if (value == null) {
                    value = "";
                }
                content = content.replace(placeholder, value);
            }
            return content;
        } catch (Exception e) {
            log.error("消息发送失败", e);
            return "";
        }
    }

    /** 根据模板获取参数映射 */
    private Map<String, String> getParametersByTemplate(
            ApprovalProcessDTO approvalProcessDTO,
            WechatTemplateEntryResp template,
            Object sourceData) {
        // 解析param_id字段，获取参数ID列表
        List<Long> paramIds = template.getConfigParamIds();
        if (paramIds.isEmpty()) {
            throw new BusinessException("当前模板参数有误");
        }
        // 查询所有参数配置
        List<WechatTemplateParamEntryResp> paramList =
                sysWechatTemplateApi.listParamsByIds(
                        template.getProjectNo(), template.getSubjectId(), paramIds);
        // 转为Map，key为param_slug
        Map<String, WechatTemplateParamEntryResp> paramMap =
                paramList.stream()
                        .collect(
                                Collectors.toMap(
                                        WechatTemplateParamEntryResp::getTemplateParamSlug,
                                        p -> p,
                                        (v1, v2) -> v1));
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, WechatTemplateParamEntryResp> entry : paramMap.entrySet()) {
            // 例如 "p-systemName"
            String slug = entry.getKey();
            WechatTemplateParamEntryResp param = entry.getValue();
            // 去掉前缀得到真实标识
            String formatSlug = slug.replace("p-", "");
            String replaceText =
                    getParamTextWithCommonParam(approvalProcessDTO, formatSlug, sourceData);
            result.put(slug, replaceText);
        }
        return result;
    }

    /** 处理通用参数（系统名称、当前时间、当前登录用户、前端URL、以及Model中的基础字段） */
    private String getParamTextWithCommonParam(
            ApprovalProcessDTO approvalProcessDTO, String slug, Object sourceData) {
        Long createdBy = (Long) getFieldValueFromSource(sourceData, "created_by");
        UserDetailResp createByUser = sysUserApi.getUserDetailByUserId(createdBy);
        // 系统内置参数
        switch (slug) {
            case "systemName":
                if (AppContext.getProjectNo().contains("company")) {
                    return "外部对象管理系统";
                }
            case "frontUrl":
                return "http://192.168.60.6:5008";
            case "currentTime":
                return LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            case "loginUser":
                if (approvalProcessDTO.getApplicantId().equals(9999999L)) {
                    return "系统自动审批";
                }
                try {
                    UserDetailResp loginUser =
                            sysUserApi.getUserDetailByUserId(approvalProcessDTO.getApplicantId());
                    return loginUser.getUserName();
                } catch (Exception e) {
                    return "";
                }
            case "created_by":
                if (createByUser != null && createByUser.getUserName() != null) {
                    return createByUser.getUserName();
                } else {
                    return String.valueOf(createdBy);
                }
            case "created_date":
                LocalDateTime createdDate =
                        (LocalDateTime) getFieldValueFromSource(sourceData, "created_date");
                return createdDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            case "id":
                Long recordId = (Long) getFieldValueFromSource(sourceData, "record_id");
                return String.valueOf(recordId);
            case "business_no":
                return (String) getFieldValueFromSource(sourceData, "business_number");
            case "version_no_label":
                Integer versionNo = (Integer) getFieldValueFromSource(sourceData, "version_no");
                return Objects.isNull(versionNo) || versionNo == 1 ? "" : "V" + versionNo;
            case "subject_name":
                List<SubjectBO> subjectList = subjectApi.getSubjectList();
                Long subjectId = (Long) getFieldValueFromSource(sourceData, "subject_id");
                if (!subjectList.isEmpty()) {
                    return subjectList.stream()
                            .filter(s -> subjectId.equals(s.getId()))
                            .findFirst()
                            .map(SubjectBO::getSubjectName)
                            .orElse("");
                }
                return "";
            case "reason":
                String reason = "";
                if (Objects.nonNull(approvalProcessDTO.getReason())) {
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        JsonNode jsonNode =
                                mapper.readTree(approvalProcessDTO.getReason().toString());
                        reason = jsonNode.path("comment").asText("");
                    } catch (Exception e) {
                        reason = "";
                    }
                }
                return reason;
            default:
                // 其他字段直接从sourceData中获取
                Object value = getFieldValueFromSource(sourceData, slug);
                return value != null ? value.toString() : "";
        }
    }

    /** 从sourceData中获取指定字段的值，支持Map或普通Java对象 */
    private Object getFieldValueFromSource(Object sourceData, String fieldName) {
        if (sourceData == null) {
            return null;
        }
        if (sourceData instanceof Map) {
            return ((Map<?, ?>) sourceData).get(fieldName);
        } else {
            return null;
        }
    }

    // 获取消息接收人列表------------------------------------------------------------------
    private Set<Long> getSendUserList(ApprovalProcessDTO approvalProcessDTO, Long templateId) {
        // 1. 查询微信模板
        WechatTemplateDetailResp sysWeChatTemplate =
                sysWechatTemplateApi.getTemplateById(templateId);
        if (sysWeChatTemplate == null) {
            return Collections.emptySet();
        }

        Set<Long> userList = new HashSet<>();

        // 3. 接收人类型列表 [1,4]
        List<Long> acceptTypeList = sysWeChatTemplate.getTemplateAcceptType();

        // 5. 循环处理每种接收类型
        for (Long item : acceptTypeList) {
            SysConfigItemResp aliasConfig = sysConfigCategoryApi.getConfigItemsById(item);

            // ================== 额外接收人 ==================
            if ("login_user".equals(aliasConfig.getValue())) {
                userList.add(approvalProcessDTO.getCurrentUserId());
                continue;
            }

            // ================== 额外接收人 ==================
            if ("extra_receiver".equals(aliasConfig.getValue())) {
                userList.addAll(sysWeChatTemplate.getExtraReceiverIds());
                continue;
            }

            // ================== 申请人 ==================
            if ("apply_person".equals(aliasConfig.getValue())) {
                userList.add(approvalProcessDTO.getCreatedBy());
                continue;
            }

            // ================== 下一步审批人 ==================
            if ("next_approval_person".equals(aliasConfig.getValue())) {
                LambdaQueryWrapper<ApprovalChain> approvalWrapper = new LambdaQueryWrapper<>();
                approvalWrapper.eq(
                        ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
                approvalWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
                approvalWrapper
                        .eq(ApprovalChain::getCurrentStep, approvalProcessDTO.getCurrentStep())
                        .last("LIMIT 1");
                ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalWrapper);
                if (approvalChain != null) {
                    List<Long> approverUserList = getApproverUserList(approvalChain);
                    userList.addAll(approverUserList);
                }
                continue;
            }

            // ================== 上一步审批人（注释保留） ==================
            if ("prev_approval_person".equals(aliasConfig.getValue())) {
                LambdaQueryWrapper<ApprovalChain> approvalWrapper = new LambdaQueryWrapper<>();
                approvalWrapper.eq(
                        ApprovalChain::getSerialNumber, approvalProcessDTO.getSerialNumber());
                approvalWrapper.eq(ApprovalChain::getSerialNo, approvalProcessDTO.getSerialNo());
                approvalWrapper
                        .eq(ApprovalChain::getCurrentStep, approvalProcessDTO.getCurrentStep())
                        .last("LIMIT 1");
                ApprovalChain approvalChain = approvalChainMapper.selectOne(approvalWrapper);
                if (approvalChain != null) {
                    List<Long> approverUserList = getApproverUserList(approvalChain);
                    userList.addAll(approverUserList);
                }
                continue;
            }

            // ================== 已审批人 ==================
            if ("approved_person".equals(aliasConfig.getValue())) {
                List<Object> approvalUids =
                        ToolUtils.formatArrayItem(approvalProcessDTO.getApprovalUids());
                List<Long> approverUserList =
                        approvalUids.stream()
                                .map(approvalUid -> Long.valueOf((String) approvalUid))
                                .toList();
                userList.addAll(approverUserList);
                continue;
            }

            // ================== 状态关联人（注释保留） ==================
            if ("status_change_related".equals(aliasConfig)) {
                // 注释逻辑保留，不实现
            }
        }

        // 6. 二维数组扁平化 + 去重
        //        List<Object> result = userList.stream().flatMap(List::stream).distinct().toList();

        return userList;
    }

    // 根据审批规则获取可操作用户ID列表-----------------------------------------------------------------
    private List<Long> getApproverUserList(ApprovalChain approvalChain) {
        List<Long> userList = new ArrayList<>();
        switch (approvalChain.getApprovalRule()) {
            case "single_approval" -> {
                // 直接添加审批人ID
                userList.add(approvalChain.getApproverId());
            }
            case "one_of_role_group", "all_members_done", "percent_of_group" -> {
                // 查询角色下所有用户
                List<SysUserPageResp> allApprovers =
                        sysUserApi.getUsersByRoleId(approvalChain.getApproveRoleId());

                // 提取 user_id 列表
                List<Long> roleUserIds =
                        allApprovers.stream().map(SysUserPageResp::getUserId).toList();
                userList.addAll(roleUserIds);
            }
            case "child_all" -> {}
        }

        return userList;
    }
}
