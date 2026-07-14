package com.jdec.platform.common_business.api.dto.response;

import com.jdec.platform.common_business.api.dto.common.PersonalizedModuleDTO;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class PersonalizedModuleResp implements Serializable {

    private Long moduleId;

    private String moduleName;

    List<PersonalizedModuleDTO> columnRespList;
}
