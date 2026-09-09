package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.data.ListDataProcessService;
import java.util.List;
import java.util.Map;

/** 行序号处理 */
public class SerialNumberListDataProcessServiceImpl implements ListDataProcessService {
    @Override
    public void processAssembleData(
            List<Map<String, Object>> dataList,
            Map<Object, Object> returnMap,
            DataIntegrationContext context) {}

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return null;
    }
}
