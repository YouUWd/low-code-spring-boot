package com.jdec.platform.common.biz.monitorlog.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.biz.monitorlog.handler.AbstractMonitorLogSaveHandler;
import com.jdec.platform.shared.constant.MonitorLogConstant;
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
