package com.jdec.platform.common_business.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormField {

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "字段中文名")
    private String fieldTitle;

    // 字段权限 0:无权限，前端显示***;1:有查看权限;2有编辑权限
    @Schema(description = "字段权限")
    private Integer permissionCode;

    private String relationBusinessNo;

    private String relationName;

    @Schema(description = "字段表名")
    private String tableName;
}
