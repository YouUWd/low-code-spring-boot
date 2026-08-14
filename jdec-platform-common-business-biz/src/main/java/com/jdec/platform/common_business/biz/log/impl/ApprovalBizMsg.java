package com.jdec.platform.common_business.biz.log.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.google.common.collect.Sets;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.HrUserApiOpenFeignClient;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.dto.BizWechatMsgDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.enums.BizMsgEnum;
import com.jdec.platform.common_business.biz.log.BizMsg;
import com.jdec.platform.common_business.biz.log.ComBizLogEventPublisher;
import com.jdec.platform.config.api.dto.request.SendWechatMessageReq;
import com.jdec.platform.hr.api.bo.UserBO;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ApprovalBizMsg implements BizMsg {

    private final Set<BizMsgEnum> referSets = Set.of(BizMsgEnum.APPROVAL_CHAIN);

    private final ConfigCenterApiOpenFeignClient configCenterApiOpenFeignClient;

    private final HrUserApiOpenFeignClient hrUserApiOpenFeignClient;

    @Override
    public Boolean referToBizMsg(BizMsgEnum msgEnum) {
        return referSets.contains(msgEnum);
    }

    @Override
    public Boolean processBizMsg(BizMsgEnum msgEnum, Object msg) {
        if (msg == null) {
            return Boolean.FALSE;
        }
        BizWechatMsgDTO bizWechatMsgDTO = (BizWechatMsgDTO) msg;
        if (CollectionUtil.isEmpty(bizWechatMsgDTO.getAcceptorList())
                || bizWechatMsgDTO.getWechatTemplate() == null
                || bizWechatMsgDTO.getWechatTemplate().getWechatTemplateDetailResp() == null) {
            log.warn("processBizMsg empty param not send");
            return Boolean.FALSE;
        }
        try {
            TokenContextHolder.setCurrentUserToken(bizWechatMsgDTO.getToken());
            ConfigApiResponse<List<UserBO>> configApiResponse =
                    hrUserApiOpenFeignClient.listByIds(
                            Sets.newHashSet(bizWechatMsgDTO.getAcceptorList()));
            if (!ConfigApiResponse.isSuccessResponse(configApiResponse)
                    || CollectionUtil.isEmpty(configApiResponse.getData())) {
                log.warn("processBizMsg empty acceptors not send");
            }
            List<String> accptorList =
                    configApiResponse.getData().stream()
                            .map(x -> x.getShortName())
                            .collect(Collectors.toList());
            String templateTitle =
                    bizWechatMsgDTO
                            .getWechatTemplate()
                            .getWechatTemplateDetailResp()
                            .getTemplateTitle();
            String templateContent =
                    bizWechatMsgDTO
                            .getWechatTemplate()
                            .getWechatTemplateDetailResp()
                            .getTemplateContent();
            if (CollectionUtil.isNotEmpty(bizWechatMsgDTO.getParam())) {
                if (templateContent != null) {
                    for (Map.Entry<String, String> e : bizWechatMsgDTO.getParam().entrySet()) {
                        templateContent = templateContent.replace(e.getKey(), e.getValue());
                    }
                }
                if (templateTitle != null) {
                    for (Map.Entry<String, String> e : bizWechatMsgDTO.getParam().entrySet()) {
                        templateTitle = templateTitle.replace(e.getKey(), e.getValue());
                    }
                }
            }
            SendWechatMessageReq req = new SendWechatMessageReq();
            req.setMessageContent(templateContent);
            req.setMessageTitle(templateTitle);
            req.setReceivers(accptorList);
            req.setMessageType("markdown");
            // 发送消息
            ConfigApiResponse<Boolean> apiResponse =
                    configCenterApiOpenFeignClient.sendMessage(bizWechatMsgDTO.getSubjectId(), req);
            // 记录消息日志
            if (ConfigApiResponse.isSuccessResponse(apiResponse)) {
                // 业务单号 从消息参数中获取
                String businessNo = bizWechatMsgDTO.getParam().get("p-business_no");
                // 版本号
                String versionNo = bizWechatMsgDTO.getParam().get("p-version_no_label");
                BizLogDTO bizLogDTO =
                        BizLogDTO.builder()
                                .projectNo(bizWechatMsgDTO.getProjectNo())
                                .token(bizWechatMsgDTO.getToken())
                                .logType(bizWechatMsgDTO.getLogType())
                                .platform(bizWechatMsgDTO.getPlatform())
                                .browser(bizWechatMsgDTO.getBrowser())
                                .ip(bizWechatMsgDTO.getIp())
                                .origin(bizWechatMsgDTO.getOrigin())
                                .loginUserId(bizWechatMsgDTO.getLoginUserId())
                                .mockUserId(bizWechatMsgDTO.getMockUserId())
                                .subjectId(bizWechatMsgDTO.getSubjectId())
                                .roleId(bizWechatMsgDTO.getRoleId())
                                .menuId(bizWechatMsgDTO.getMenuId())
                                .moduleId(bizWechatMsgDTO.getModuleId())
                                .moduleName(bizWechatMsgDTO.getModuleName())
                                .dataId(bizWechatMsgDTO.getDataId())
                                // 消息完整的内容
                                .logContent(templateContent)
                                .logRemark(null)
                                .hrefName(businessNo)
                                .orderVersion(versionNo)
                                .controlName(templateTitle)
                                .messageReceivers(bizWechatMsgDTO.getAcceptorList())
                                .build();
                ComBizLogEventPublisher.publishLogEvent(BizLogEnum.MSG, bizLogDTO);
            }
            return Boolean.TRUE;
        } catch (Exception e) {
            log.error("processBizMsg logEnum={},log={},e=", msgEnum, msg.toString(), e);
        } finally {
            TokenContextHolder.clear();
        }
        return Boolean.FALSE;
    }
}
