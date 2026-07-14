package com.jdec.platform.common_business.api.dto.request;

import com.jdec.platform.common_business.api.dto.common.PersonalizedModuleDTO;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class PersonalizedModuleReq implements Serializable {

    private Long moduleId;

    private String moduleName;

    List<PersonalizedModuleDTO> columnRespList;
}
