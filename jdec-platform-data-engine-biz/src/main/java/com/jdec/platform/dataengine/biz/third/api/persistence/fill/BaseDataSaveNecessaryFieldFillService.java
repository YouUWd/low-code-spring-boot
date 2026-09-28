package com.jdec.platform.dataengine.biz.third.api.persistence.fill;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.third.api.persistence.SaveReferService;
import java.util.Map;

public interface BaseDataSaveNecessaryFieldFillService extends SaveReferService {

    public Map<String, Object> fillData(RecordSaveContext recordSaveContext);
}
