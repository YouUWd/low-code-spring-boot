package com.jdec.platform.data.biz.monitorlog.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.constant.MonitorLogConstant;
import com.jdec.platform.data.biz.monitorlog.handler.AbstractMonitorLogSaveHandler;
import org.springframework.stereotype.Component;

/** 操作日志保存处理器 */
@Component
public class OperationLogSaveHandler extends AbstractMonitorLogSaveHandler {

    public OperationLogSaveHandler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public Integer supportType() {
        return MonitorLogConstant.TYPE_OPERATION_LOG;
    }
}
