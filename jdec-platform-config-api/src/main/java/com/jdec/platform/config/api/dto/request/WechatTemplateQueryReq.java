package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分页查询企业微信消息通知模板请求 DTO */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页查询企业微信消息通知模板请求")
public class WechatTemplateQueryReq extends PageRequest {

    @Schema(description = "消息标题(模糊查询)", example = "审批")
    private String templateTitle;

    @Schema(description = "模块 ID", example = "27")
    private Long moduleId;
}
