package com.jdec.platform.common_business.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.*;

@Data
@EqualsAndHashCode
@Schema(description = "模块数据变更对象")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModuleDataChangeDTO implements Serializable {

    @Schema(description = "模块ID")
    private Long moduleId;

    private String moduleName;

    //    @Schema(description = "修改的数据表")
    //    List<TableDataChangeDTO> modifyTables;

    private DataChangeActionDTO actions;
}
