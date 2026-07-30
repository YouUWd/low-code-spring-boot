package com.jdec.platform.common_business.biz.log.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.google.common.collect.Sets;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.HrUserApiOpenFeignClient;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.BizWechatMsgDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.BizMsgEnum;
import com.jdec.platform.common_business.biz.log.BizMsg;
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
        if (CollectionUtil.isNotEmpty(bizWechatMsgDTO.getAcceptorList())
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
            configCenterApiOpenFeignClient.sendMessage(bizWechatMsgDTO.getSubjectId(), req);
            return Boolean.TRUE;
        } catch (Exception e) {
            log.error("processBizMsg logEnum={},log={},e=", msgEnum, msg.toString(), e);
        } finally {
            TokenContextHolder.clear();
        }
        return Boolean.FALSE;
    }
}
