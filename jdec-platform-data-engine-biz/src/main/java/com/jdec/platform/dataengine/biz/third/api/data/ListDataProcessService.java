package com.jdec.platform.dataengine.biz.third.api.data;

import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.ReferService;
import java.util.List;
import java.util.Map;

/** 行数据 处理 接口 */
public interface ListDataProcessService extends ReferService {

    public void processAssembleData(
            List<Map<String, Object>> dataList,
            Map<Object, Object> returnMap,
            DataIntegrationContext context);
}
