package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.biz.enums.SaveType;

public interface SaveReferService {

    Boolean isReferTo(SaveType type);
}
