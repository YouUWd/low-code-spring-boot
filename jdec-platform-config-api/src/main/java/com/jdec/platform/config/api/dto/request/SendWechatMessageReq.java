package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 发送微信消息请求 DTO */
@Data
@Schema(description = "发送微信消息请求")
public class SendWechatMessageReq {

    @Schema(description = "模板标题/消息标题")
    private String messageTitle;

    @Schema(description = "接收人账号/企业微信User ID")
    private String receiver;

    @Schema(description = "消息内容 (Markdown格式)")
    private String messageContent;

    @Schema(description = "消息类型，默认为 markdown")
    private String messageType;
}
