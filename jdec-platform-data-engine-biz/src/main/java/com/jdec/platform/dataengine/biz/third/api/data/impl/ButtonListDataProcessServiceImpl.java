package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.ListDataProcessService;
import java.util.List;
import java.util.Map;

/** 数据 行上的按钮处理,包含 审批链业务和 非审批链业务 */
public class ButtonListDataProcessServiceImpl implements ListDataProcessService {
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
