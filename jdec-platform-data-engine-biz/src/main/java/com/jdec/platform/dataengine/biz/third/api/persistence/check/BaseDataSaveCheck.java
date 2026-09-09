package com.jdec.platform.dataengine.biz.third.api.persistence.check;

import com.jdec.platform.dataengine.biz.dto.context.ModuleDataCheckContext;
import com.jdec.platform.dataengine.biz.third.api.persistence.SaveReferService;

public interface BaseDataSaveCheck extends SaveReferService {

    Boolean check(ModuleDataCheckContext context);
}
