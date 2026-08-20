package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/** 微信模板参数详情响应 DTO */
@Data
@Schema(description = "微信模板参数详情响应")
public class WechatTemplateParamEntryResp {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "项目编号")
    private String projectNo;

    @Schema(description = "主体 ID")
    private Long subjectId;

    @Schema(description = "参数名称")
    private String templateParamName;

    @Schema(description = "参数标识")
    private String templateParamSlug;

    @Schema(description = "参数描述")
    private String templateParamDesc;

    @Schema(description = "关联模块 ID")
    private Long moduleId;

    @Schema(description = "关联模块名称")
    private String moduleName;

    @Schema(description = "关联字段 ID")
    private Long fieldId;

    @Schema(description = "关联字段显示名称")
    private String fieldName;

    @Schema(description = "是否需要转换: 0-否, 1-是")
    private Integer convertFlag;

    @Schema(description = "转换方法")
    private String templateParamConvertMethod;

    @Schema(description = "创建人姓名")
    private String createdName;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "更新时间")
    private LocalDateTime updatedDate;
}
