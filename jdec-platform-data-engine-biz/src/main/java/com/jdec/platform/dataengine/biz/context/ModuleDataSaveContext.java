package com.jdec.platform.dataengine.biz.context;

import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleDataSaveContext {

    private SaveType type;

    private Long actionId;

    private Map<Long, Map<Long, RowChangeDTO>> changeMap;
}
