package com.jdec.platform.common_business.biz.change;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
@Schema(description = "数据行变更对象")
@Builder
public class RowChangeDTO implements Serializable {

    @Schema(description = "数据行")
    private List<FieldChangeDTO> columnList;

    private String uniqName;
}
