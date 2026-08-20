package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleInfoResp implements Serializable {

    private ModuleResp module;

    private List<ModuleTableResp> moduleTables;

    private List<SimpleField> simpleFields;

    private List<ModuleStatus> moduleStatuses;
}
