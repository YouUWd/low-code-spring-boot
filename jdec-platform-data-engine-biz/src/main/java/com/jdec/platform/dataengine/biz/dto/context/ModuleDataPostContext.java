package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import com.jdec.platform.dataengine.biz.dto.BasicSaveModel;
import java.util.Map;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ModuleDataPostContext extends BasicSaveModel {

    private Long actionId;

    private NestedModuleDataDTO nestedModuleDataDTO;

    private Map<Long, Map<Long, RowChangeDTO>> changeMap;
}
