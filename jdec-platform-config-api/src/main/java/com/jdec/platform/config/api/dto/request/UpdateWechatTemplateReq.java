package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 更新企业微信消息通知模板请求 DTO */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新企业微信消息通知模板请求")
public class UpdateWechatTemplateReq extends CreateWechatTemplateReq {

    @Schema(description = "主键 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;
}
