package com.jdec.platform.dataengine.biz.third.api.persistence.check;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.third.api.persistence.SaveReferService;

public interface BaseDataSaveCheck extends SaveReferService {

    Boolean check(RecordSaveContext context);
}
