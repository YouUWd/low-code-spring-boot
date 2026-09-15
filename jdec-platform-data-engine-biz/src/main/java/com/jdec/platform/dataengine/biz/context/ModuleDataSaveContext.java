package com.jdec.platform.dataengine.biz.context;

import com.jdec.platform.dataengine.biz.enums.SaveType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleDataSaveContext {

    private SaveType type;

    private Long actionId;
}
