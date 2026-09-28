package com.jdec.platform.dataengine.biz.third.api;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;

public interface ReferService {

    Boolean isReferTo(QueryType queryType);
}
