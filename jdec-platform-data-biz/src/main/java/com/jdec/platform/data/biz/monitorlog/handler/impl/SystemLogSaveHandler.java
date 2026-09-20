package com.jdec.platform.data.biz.monitorlog.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.biz.monitorlog.handler.AbstractMonitorLogSaveHandler;
import com.jdec.platform.shared.constant.MonitorLogConstant;
import org.springframework.stereotype.Component;

/** 系统日志保存处理器 */
@Component
public class SystemLogSaveHandler extends AbstractMonitorLogSaveHandler {

    public SystemLogSaveHandler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public Integer supportType() {
        return MonitorLogConstant.TYPE_SYSTEM_LOG;
    }
}
