package com.jdec.platform.common_business.api.dto.common;

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
    private List<com.jdec.platform.common_business.api.dto.common.FieldChangeDTO> columnList;

    private String uniqName;
}
