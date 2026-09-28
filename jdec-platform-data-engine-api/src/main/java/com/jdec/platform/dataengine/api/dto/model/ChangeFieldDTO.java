package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段变更对比")
public class ChangeFieldDTO implements java.io.Serializable {

    @Schema(description = "字段名")
    String fieldName;

    @Schema(description = "旧值")
    String old;

    @Schema(description = "新值")
    String newer;
}
