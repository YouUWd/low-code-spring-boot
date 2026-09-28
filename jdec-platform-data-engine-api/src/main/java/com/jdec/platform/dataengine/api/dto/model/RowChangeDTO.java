package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.*;

@Data
@EqualsAndHashCode
@Schema(description = "数据行变更对象")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RowChangeDTO implements Serializable {

    @Schema(description = "数据行")
    private List<FieldChangeDTO> columns;

    private String name;

    private String action;

    private Long dataId;

    private List<ModuleChangeDTO> children;
}
