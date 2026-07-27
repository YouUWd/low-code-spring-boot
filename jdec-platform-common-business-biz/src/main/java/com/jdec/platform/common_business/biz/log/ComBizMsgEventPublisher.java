package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.enums.BizMsgEnum;
import com.jdec.platform.shared.utils.SpringBeanUtil;

public class ComBizMsgEventPublisher {

    public static void publishMsgEvent(BizMsgEnum bizMsgEnum, Object log) {
        if (bizMsgEnum != null && log != null) {
            SpringBeanUtil.getBean(LogMsgDisruptor.class).publishMsgEvent(bizMsgEnum, log);
        }
    }
}
