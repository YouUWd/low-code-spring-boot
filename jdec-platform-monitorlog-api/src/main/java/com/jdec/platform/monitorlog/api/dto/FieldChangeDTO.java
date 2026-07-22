package com.jdec.platform.monitorlog.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FieldChangeDTO {
    @NotNull(message = "字段名不能为空")
    private Object oldVal;

    @NotNull(message = "字段值不能为空")
    private Object newVal;
}
