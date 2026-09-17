package com.jdec.platform.data.biz.monitorlog.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.constant.MonitorLogConstant;
import com.jdec.platform.data.biz.monitorlog.handler.AbstractMonitorLogSaveHandler;
import org.springframework.stereotype.Component;

/** 消息日志保存处理器 */
@Component
public class MessageLogSaveHandler extends AbstractMonitorLogSaveHandler {

    public MessageLogSaveHandler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public Integer supportType() {
        return MonitorLogConstant.TYPE_MESSAGE_LOG;
    }
}
