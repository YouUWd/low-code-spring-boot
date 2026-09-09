package com.jdec.platform.dataengine.biz.third.api;

import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import java.util.Map;

public interface DetailBizQueryService {
    Map<Object, Object> queryDetailBiz(DetailQueryContext context);
}
