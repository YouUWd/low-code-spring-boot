package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 企业微信消息通知模板详情响应 DTO */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "企业微信消息通知模板详情响应")
public class WechatTemplateDetailResp extends WechatTemplateEntryResp {}
