package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "行记录变更对比")
public class ChangeRowDTO implements java.io.Serializable {
    @Schema(description = "上一个生效版本")
    Integer prevEnable;

    @Schema(description = "当前版本")
    Integer current;

    @Schema(description = "变更字段列表")
    List<ChangeFieldDTO> changeFields;
}
