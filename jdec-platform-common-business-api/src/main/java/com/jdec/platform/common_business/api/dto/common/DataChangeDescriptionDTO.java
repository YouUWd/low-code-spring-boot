package com.jdec.platform.common_business.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.*;

@Data
@EqualsAndHashCode
@Schema(description = "模块数据变更对象")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataChangeDescriptionDTO implements Serializable {

    private String reason;

    private List<ModuleDataChangeDTO> change;
}
