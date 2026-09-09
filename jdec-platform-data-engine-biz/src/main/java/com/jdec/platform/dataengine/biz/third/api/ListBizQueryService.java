package com.jdec.platform.dataengine.biz.third.api;

import com.jdec.platform.dataengine.biz.dto.context.ListQueryContext;
import java.util.Map;

/** 详情数据查询接口 */
public interface ListBizQueryService extends ReferService {
    Map<Object, Object> queryListBiz(ListQueryContext context);
}
