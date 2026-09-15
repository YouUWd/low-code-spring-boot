package com.jdec.platform.dataengine.biz.third.api.detail;

import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.third.api.ReferService;
import java.util.List;

public interface DetailBizReferQueryService extends ReferService {

    List<RecordDataDTO> queryDetailBizRefer(DetailQueryContext context);
}
