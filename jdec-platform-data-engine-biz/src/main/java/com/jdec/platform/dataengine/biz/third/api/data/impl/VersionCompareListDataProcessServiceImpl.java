package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.Map;

/** 详情查看单据版本对比信息获取 */
public class VersionCompareListDataProcessServiceImpl implements DetailDataProcessService {

    @Override
    public void processAssembleData(
            Map<Object, Object> moduleData,
            Map<Object, Object> returnMap,
            DataIntegrationContext context) {}

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return null;
    }
}
