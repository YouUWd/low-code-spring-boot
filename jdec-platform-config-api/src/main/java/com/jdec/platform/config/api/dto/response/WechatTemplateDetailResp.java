package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 企业微信消息通知模板详情响应 DTO */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "企业微信消息通知模板详情响应")
public class WechatTemplateDetailResp extends WechatTemplateEntryResp {

    @Schema(description = "消息内容")
    private String templateContent;

    @Schema(description = "额外接收人 ID 数组")
    private List<Long> extraReceiverIds;

    @Schema(description = "额外接收人姓名(逗号分隔)")
    private String extraReceiverNames;

    @Schema(description = "消息参数配置 ID 数组")
    private List<Long> configParamIds;
}
