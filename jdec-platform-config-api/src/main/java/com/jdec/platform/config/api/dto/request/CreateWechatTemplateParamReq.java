package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 创建微信模板参数请求 DTO */
@Data
@Schema(description = "创建微信模板参数请求")
public class CreateWechatTemplateParamReq {

    @Schema(description = "参数名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "姓名")
    private String templateParamName;

    @Schema(description = "参数标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "userName")
    private String templateParamSlug;

    @Schema(description = "参数描述", example = "用户姓名")
    private String templateParamDesc;

    @Schema(description = "关联模块 ID", example = "10")
    private Long moduleId;

    @Schema(description = "关联字段 ID", example = "50")
    private Long fieldId;

    @Schema(description = "是否需要转换: 0-否, 1-是", example = "0")
    private Integer convertFlag;

    @Schema(description = "转换方法", example = "formatDate")
    private String templateParamConvertMethod;
}
