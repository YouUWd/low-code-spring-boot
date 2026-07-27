package com.jdec.platform.common_business.biz.log;

import com.lmax.disruptor.EventFactory;

public class LogMsgEventFactory implements EventFactory<LogMsgEvent> {
    @Override
    public LogMsgEvent newInstance() {
        return new LogMsgEvent();
    }
}
