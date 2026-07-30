package com.jdec.platform.common_business.biz.dto;

import com.jdec.platform.config.api.dto.response.WechatTemplateDetailResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateParamEntryResp;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class WechatTemplateDetailDTO implements Serializable {

    private WechatTemplateDetailResp wechatTemplateDetailResp;

    private List<String> templateAcceptTypeEnum;

    private List<WechatTemplateParamEntryResp> templateParamEntryRespList;
}
