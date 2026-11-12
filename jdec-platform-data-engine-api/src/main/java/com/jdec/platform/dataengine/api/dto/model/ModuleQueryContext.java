package com.jdec.platform.dataengine.api.dto.model;

import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Data
public class ModuleQueryContext extends ReqParamData implements Serializable {

    private Long moduleId;

    private Boolean isBuildFieldPermission;

    private Boolean isBuildFieldPro;
}
