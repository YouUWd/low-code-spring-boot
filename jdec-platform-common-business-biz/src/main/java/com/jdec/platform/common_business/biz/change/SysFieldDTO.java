package com.jdec.platform.common_business.biz.change;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

@Data
public class SysFieldDTO implements Serializable {

    @Schema(description = "字段名")
    private String fieldName;

    @Schema(description = "字段显示名")
    private String fieldShowName;

    @Schema(description = "字段类型")
    private String fieldType;
}
