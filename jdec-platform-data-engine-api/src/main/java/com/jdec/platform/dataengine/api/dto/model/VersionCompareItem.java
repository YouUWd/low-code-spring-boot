package com.jdec.platform.dataengine.api.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本对比")
public class VersionCompareItem {
    @Schema(description = "上一版本值")
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    private Object pre;

    @Schema(description = "版本内上一版本值")
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    private Object currentOld;

    @Schema(description = "当前值")
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    private Object current;
}
