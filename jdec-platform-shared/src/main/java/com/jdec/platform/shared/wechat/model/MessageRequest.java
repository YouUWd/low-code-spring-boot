package com.jdec.platform.shared.wechat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发送消息请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    /** 接收人用户ID，多个用|分隔 */
    @JsonProperty("touser")
    private String toUser;

    /** 消息类型 */
    @JsonProperty("msgtype")
    private String msgType;

    /** 应用ID */
    @JsonProperty("agentid")
    private Integer agentId;

    /** 文本消息内容 */
    @JsonProperty("text")
    private TextContent text;

    /** Markdown消息内容 */
    @JsonProperty("markdown")
    private MarkdownContent markdown;

    /** 是否保密消息 */
    @JsonProperty("safe")
    @Builder.Default
    private Integer safe = 0;

    /** 文本消息内容 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextContent {
        /** 消息内容 */
        private String content;
    }

    /** Markdown消息内容 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarkdownContent {
        /** 消息内容 */
        private String content;
    }
}
