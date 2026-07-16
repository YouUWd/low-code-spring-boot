package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.enums.BizLogEnum;

public interface BizLog {

    public Boolean referToBizLog(BizLogEnum logEnum);

    public Boolean recordBizLog(BizLogEnum logEnum, Object log);
}
