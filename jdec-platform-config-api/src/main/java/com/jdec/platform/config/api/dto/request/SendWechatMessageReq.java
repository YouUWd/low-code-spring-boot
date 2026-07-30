package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 发送微信消息请求 DTO */
@Data
@Schema(description = "发送微信消息请求")
public class SendWechatMessageReq {

    @Schema(description = "模板标题/消息标题")
    private String messageTitle;

    @Schema(description = "接收人账号列表/企业微信User ID列表")
    private List<String> receivers;

    @Schema(description = "消息内容 (Markdown格式)")
    private String messageContent;

    @Schema(description = "消息类型，默认为 markdown")
    private String messageType;
}
