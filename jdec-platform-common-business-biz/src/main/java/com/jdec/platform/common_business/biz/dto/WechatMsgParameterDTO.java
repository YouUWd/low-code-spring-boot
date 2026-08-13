package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class WechatMsgParameterDTO implements Serializable {

    private Long templateId;

    private List<Long> acceptorList;

    private Map<String, String> approvalParam;

    private Map<String, String> bizParam;
}
