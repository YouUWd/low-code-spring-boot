package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分页查询微信模板参数请求 DTO */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页查询微信模板参数请求")
public class WechatTemplateParamQueryReq extends PageRequest {

    @Schema(description = "参数(模糊查询)", example = "姓名")
    private String keyword;

    @Schema(description = "关联模块 ID", example = "10")
    private Long moduleId;
}
