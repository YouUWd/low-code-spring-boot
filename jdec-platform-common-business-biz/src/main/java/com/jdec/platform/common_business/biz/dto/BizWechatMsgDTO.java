package com.jdec.platform.common_business.biz.dto;

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

    private WechatTemplateDetailDTO wechatTemplate;

    private List<Long> acceptorList;

    private Map<String, String> param;

    // 关于日志的字段
    private String projectNo;

    private String logType;

    private String platform;

    private String browser;

    private String ip;

    private String origin;

    private Long loginUserId;

    private Long mockUserId;

    private Long roleId;

    private String logRemark;

    private Long menuId;

    private Long moduleId;

    private String moduleName;

    private String dataId;
}
