package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单表行数据")
public class TableRowDataDTO implements java.io.Serializable {

    @Schema(description = "行数据")
    private Map<String, Object> data;

    @Schema(description = "行记录变更对比")
    private ChangeRowDTO change;
}
