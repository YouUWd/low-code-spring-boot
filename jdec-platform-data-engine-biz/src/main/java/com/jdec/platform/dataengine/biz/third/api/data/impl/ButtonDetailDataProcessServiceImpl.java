package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.List;

/** 详情数据 button 数据 */
public class ButtonDetailDataProcessServiceImpl implements DetailDataProcessService {

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return null;
    }

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {}
}
