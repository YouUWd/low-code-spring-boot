package com.jdec.platform.data.biz.monitorlog.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.biz.monitorlog.handler.AbstractMonitorLogSaveHandler;
import com.jdec.platform.shared.constant.MonitorLogConstant;
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
