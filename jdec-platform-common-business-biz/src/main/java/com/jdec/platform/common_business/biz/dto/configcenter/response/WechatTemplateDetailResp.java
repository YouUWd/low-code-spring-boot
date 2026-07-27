package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class WechatTemplateDetailResp implements Serializable {

    private Long id;

    private String projectNo;

    private Long subjectId;

    private String templateTitle; // 消息标题

    private Long templateSendType; // 发送类型(配置ID)

    private List<Long> templateAcceptType; // 接收消息类型(配置ID数组)

    private Long templateTriggerType; // 触发类型(配置ID)

    private Long moduleId;

    private String moduleName;

    private Integer approvalFlag; // 是否审批链: 0-否, 1-是

    private String templateContent; // 消息内容

    private List<Long> extraReceiverIds; // 	额外接收人 ID 数组

    private String extraReceiverNames; // 额外接收人姓名(逗号分隔)

    private List<Long> configParamIds; // 消息参数配置 ID 数组

    private List<String> templateAcceptTypeEnum;

    private List<WechatTemplateParamEntryResp> templateParamEntryRespList;

    private String createdBy;

    private String createdDate;

    private String createdName;

    private String updatedBy;

    private String updatedDate;

    private String updatedName;
}
