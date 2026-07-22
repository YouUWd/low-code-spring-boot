package com.jdec.platform.monitorlog.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
public class DataChangeDTO {
    @NotNull(message = "键值不能为空")
    private String keyName;

    @NotNull(message = "键值不能为空")
    private String keyValue;

    @NotEmpty(message = "必须至少包含一个修改字段")
    private Map<String, FieldChangeDTO> changeMap;
}
