package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.List;

/** 详情查看获取单据审批链相关信息 */
public class ApprovalDataProcessServiceImpl implements DetailDataProcessService {

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return null;
    }

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {}
}
