package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.biz.dto.ReqParamData;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Data
public class ModuleQueryContext extends ReqParamData implements Serializable {

    private Long moduleId;

    private Boolean isPermission;

    private Boolean isField;
}
