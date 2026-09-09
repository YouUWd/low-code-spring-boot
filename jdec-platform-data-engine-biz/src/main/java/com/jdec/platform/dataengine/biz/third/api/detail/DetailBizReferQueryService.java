package com.jdec.platform.dataengine.biz.third.api.detail;

import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.third.api.ReferService;
import java.util.Map;

public interface DetailBizReferQueryService extends ReferService {

    Map<Object, Object> queryDetailBizRefer(DetailQueryContext context);
}
