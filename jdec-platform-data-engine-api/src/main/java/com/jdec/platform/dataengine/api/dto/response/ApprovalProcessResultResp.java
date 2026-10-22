package com.jdec.platform.dataengine.api.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalProcessResultResp implements Serializable {

    private NestedModuleDataDTO nestedModuleData;

    private JsonNode remark;

    private JsonNode referenceDataRemark;
}
