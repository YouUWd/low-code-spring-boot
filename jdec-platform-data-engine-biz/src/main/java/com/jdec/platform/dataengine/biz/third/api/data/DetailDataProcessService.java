package com.jdec.platform.dataengine.biz.third.api.data;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.ReferService;
import java.util.List;

/** 详情接口数据处理接口类 */
public interface DetailDataProcessService extends ReferService {

    void processAssembleData(List<NestedModuleDataDTO> records, DataIntegrationContext context);
}
