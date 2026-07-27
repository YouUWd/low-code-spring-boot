package com.jdec.platform.common_business.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
@Schema(description = "字段变更对象")
public class FieldChangeDTO implements Serializable {

    @Schema(description = "字段名")
    private String field;

    @Schema(description = "字段显示名")
    private String name;

    // @Schema(description = "字段类型")
    // private String fieldType;

    @Schema(description = "前值")
    private String old;

    @Schema(description = "后值")
    private String newer;
}
