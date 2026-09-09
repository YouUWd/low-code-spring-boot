package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.biz.dto.BasicModel;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class DataIntegrationContext extends BasicModel {

    private ModuleFilterInfo moduleFilterInfo;

    private Long id;
}
