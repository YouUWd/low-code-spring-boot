package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.shared.utils.SpringBeanUtil;

public class ComBizLogEventPublisher {

    public static void publishLogEvent(BizLogEnum logEnum, Object log) {
        if (logEnum != null && log != null) {
            SpringBeanUtil.getBean(LogMsgDisruptor.class).publishLogEvent(logEnum, log);
        }
    }
}
