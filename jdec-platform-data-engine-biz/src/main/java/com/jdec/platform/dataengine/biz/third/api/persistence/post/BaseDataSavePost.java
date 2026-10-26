package com.jdec.platform.dataengine.biz.third.api.persistence.post;

import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import com.jdec.platform.dataengine.biz.third.api.persistence.SaveReferService;

public interface BaseDataSavePost extends SaveReferService {

    Boolean postProcess(ModuleDataPostContext context);
}
