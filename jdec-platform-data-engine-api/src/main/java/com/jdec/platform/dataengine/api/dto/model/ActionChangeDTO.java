package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.*;

@Data
@EqualsAndHashCode
@Schema(description = "变更操作对象")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionChangeDTO implements Serializable {

    private List<RowChangeDTO> i;

    private List<RowChangeDTO> u;

    private List<RowChangeDTO> d;
}
