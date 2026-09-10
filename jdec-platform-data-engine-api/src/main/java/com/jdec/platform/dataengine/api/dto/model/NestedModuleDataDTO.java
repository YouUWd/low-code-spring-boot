package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "主子模块嵌套数据结构")
public class NestedModuleDataDTO implements Serializable {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "记录数据,代表主表的行记录数据")
    private List<RecordDataDTO> record;
}
