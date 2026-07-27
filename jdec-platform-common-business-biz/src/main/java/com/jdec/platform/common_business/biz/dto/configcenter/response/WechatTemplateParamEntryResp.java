package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import lombok.Data;

@Data
public class WechatTemplateParamEntryResp implements Serializable {

    private Long id;

    private String projectNo;

    private Long subjectId;

    private String templateParamName; // 参数名称

    private String templateParamSlug; // 	参数标识

    private String templateParamDesc; // 	参数描述

    private Long moduleId;

    private String moduleName;

    private Long fieldId; // 关联字段 ID

    private String fieldName; // 关联字段显示名称

    private Integer convertFlag; // 是否需要转换: 0-否, 1-是

    private String templateParamConvertMethod; // 	转换方法

    private String createdName; // 创建人姓名

    private String createdDate; // 创建时间

    private String updatedDate; // 更新时间
}
