package com.jdec.platform.dataengine.api.dto.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class DataPostParamDTO extends ReqParamData implements Serializable {

    private NestedModuleDataDTO originalData;

    private NestedModuleDataDTO saveData;

    private Long rootModuleId;

    private Long rootDataId;

    private String rootModuleCode;
}
