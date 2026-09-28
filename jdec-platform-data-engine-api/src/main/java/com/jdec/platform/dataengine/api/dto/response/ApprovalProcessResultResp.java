package com.jdec.platform.dataengine.api.dto.response;

import com.jdec.platform.dataengine.api.dto.model.ModuleChangeDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalProcessResultResp implements Serializable {

    private NestedModuleDataDTO nestedModuleData;

    private ModuleChangeDTO moduleChange;
}
