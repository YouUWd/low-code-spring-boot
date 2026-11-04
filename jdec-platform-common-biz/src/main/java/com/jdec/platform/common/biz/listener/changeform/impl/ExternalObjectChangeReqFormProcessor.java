package com.jdec.platform.common.biz.listener.changeform.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.SysWechatTemplateApi;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.request.SendWechatMessageReq;
import com.jdec.platform.config.api.dto.response.SysConfigItemResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.dataengine.api.event.ChangeApplyEvent;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalObjectChangeReqFormProcessor
        implements com.jdec.platform.common.biz.listener.changeform.ChangeReqFormEventProcessor {

    private static final Set<String> MODULE_CODE_SET = Set.of("COMPANY-ROOT");

    private final SysUserApi sysUserApi;

    private final SysWechatTemplateApi sysWechatTemplateApi;

    private final SysConfigCategoryApi sysConfigCategoryApi;

    private static final String template =
            """
            ## 外部对象管理系统
            ### created_by 发起了外部对象变更申请，请点击[页面连接](url)查看变更内容
            外部对象编号: <font color='comment'>business_no</font>
            外部对象全称: <font color='comment'>company_name</font>
            外部对象登记人: <font color='comment'>registration_by</font>
            变更申请人: <font color='comment'>created_by</font>
            变更申请时间: <font color='comment'>created_date</font>
            变更申请原因: <font color='comment'>modify_reason</font>
            消息接收人: <font color='comment'>message_receiver</font>
""";

    @Override
    public Boolean isSupport(ChangeApplyEvent event) {
        ModuleInfo rootModuleInfo = event.getRootModuleInfo();
        return MODULE_CODE_SET.contains(rootModuleInfo.getModuleCode());
    }

    @Override
    public void processChangeRequestEvent(ChangeApplyEvent event) {
        try {
            AppContext.setContext(event.getProjectNo(), event.getSubjectId());
            UserContext.setLoginUser(event.getLoginUser());

            List<SysConfigItemResp> sysConfigItemResps =
                    sysConfigCategoryApi.getConfigItemsByCategoryAlias("domainConfig");

            SysConfigItemResp sysConfigItemResp =
                    sysConfigItemResps.stream()
                            .filter(x -> x.getLabel().equals("前端域名"))
                            .findFirst()
                            .orElse(null);

            // http://192.168.2.14:5175/externalDetail/17?btnType=apply-change&changeRequestId=24
            Map<String, String> paramMap = new HashMap<>();

            Long id =
                    event.getBizMap().get("id") != null ? (Long) event.getBizMap().get("id") : null;
            Long sourceId =
                    event.getBizMap().get("source_id") != null
                            ? (Long) event.getBizMap().get("source_id")
                            : null;
            String url =
                    "http://"
                            + sysConfigItemResp.getValue()
                            + "externalDetail/"
                            + sourceId
                            + "?btnType=apply-change&changeRequestId="
                            + id;
            paramMap.put("url", url);
            paramMap.put(
                    "business_no",
                    event.getBizMap().get("business_no") != null
                            ? event.getBizMap().get("business_no").toString()
                            : "");
            paramMap.put(
                    "company_name",
                    event.getBizMap().get("company_name") != null
                            ? event.getBizMap().get("company_name").toString()
                            : "");
            paramMap.put(
                    "modify_reason",
                    event.getBizMap().get("modify_reason") != null
                            ? event.getBizMap().get("modify_reason").toString()
                            : "");
            LocalDateTime localDateTime = (LocalDateTime) event.getBizMap().get("created_date");
            paramMap.put(
                    "created_date",
                    localDateTime != null
                            ? DateUtil.format(localDateTime, DatePattern.NORM_DATETIME_PATTERN)
                            : "");

            Long userId = (Long) event.getBizMap().get("created_by");
            StringBuilder sb = new StringBuilder();
            List<String> messageReceiverList = new ArrayList<>();
            if (userId != null) {
                UserDetailResp userDetailResp = sysUserApi.getUserDetailByUserId(userId);
                paramMap.put(
                        "created_by", userDetailResp != null ? userDetailResp.getUserName() : "");
                sb.append(userDetailResp != null ? userDetailResp.getUserName() : "|");
                messageReceiverList.add(
                        userDetailResp != null ? userDetailResp.getShortName() : "");
            }
            Long registrationBy = (Long) event.getBizMap().get("registration_by");
            if (registrationBy != null) {
                UserDetailResp registrationByUserDetailResp =
                        sysUserApi.getUserDetailByUserId(registrationBy);
                paramMap.put(
                        "registration_by",
                        registrationByUserDetailResp != null
                                ? registrationByUserDetailResp.getUserName()
                                : "");
                sb.append(
                        registrationByUserDetailResp != null
                                ? registrationByUserDetailResp.getUserName()
                                : "");
                messageReceiverList.add(
                        registrationByUserDetailResp != null
                                ? registrationByUserDetailResp.getShortName()
                                : "");
            }
            paramMap.put("message_receiver", sb.toString());
            String messageTemplate = template;
            for (String k : paramMap.keySet()) {
                messageTemplate = messageTemplate.replace(k, paramMap.get(k));
            }

            SendWechatMessageReq req = new SendWechatMessageReq();
            req.setMessageTitle("外部对象变更申请");
            req.setMessageContent(messageTemplate);
            req.setMessageType("markdown");
            req.setReceivers(messageReceiverList);
            sysWechatTemplateApi.sendWechatMessage(event.getProjectNo(), event.getSubjectId(), req);
            log.info(
                    "send wechat message success template:{},receivers:{}",
                    messageTemplate,
                    messageReceiverList);
        } catch (Exception e) {
            log.error("send wechat message error", e);
        } finally {
            AppContext.remove();
            UserContext.remove();
        }
    }
}
