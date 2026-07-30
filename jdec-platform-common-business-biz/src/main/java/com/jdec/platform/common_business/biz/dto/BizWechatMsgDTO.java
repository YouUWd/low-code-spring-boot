package com.jdec.platform.common_business.biz.dto;

import com.jdec.platform.common_business.biz.dto.configcenter.response.WechatTemplateDetailResp;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class BizWechatMsgDTO implements Serializable {

    private String token;

    private Long subjectId;

    private WechatTemplateDetailResp wechatTemplateDetailResp;

    private List<Long> acceptorList;

    private Map<String, String> param;
}
