package com.jdec.platform.dataengine.biz.third.api;

import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import java.util.List;

public interface DetailBizQueryService {
    List<RecordDataDTO> queryDetailBiz(DetailQueryContext context);
}
