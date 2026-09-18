package com.jdec.platform.data.biz.monitorlog;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.log.MonitorApproveBase;
import com.jdec.platform.data.api.dto.log.MonitorContentBase;
import com.jdec.platform.data.api.dto.log.MonitorContentMessage;
import com.jdec.platform.data.api.dto.log.MonitorContentOperation;
import com.jdec.platform.data.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.data.biz.dsl.JooqDataContextFactory;
import com.jdec.platform.data.biz.monitorindex.service.MonitorOperateIndexService;
import com.jdec.platform.data.biz.monitorlog.handler.MonitorLogSaveHandler;
import com.jdec.platform.data.biz.monitorlog.handler.impl.MessageLogSaveHandler;
import com.jdec.platform.data.biz.monitorlog.handler.impl.OperationLogSaveHandler;
import com.jdec.platform.data.biz.monitorlog.handler.impl.SystemLogSaveHandler;
import com.jdec.platform.data.biz.monitorlog.service.MonitorLogEnrichService;
import com.jdec.platform.data.biz.monitorlog.service.MonitorLogService;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 监控日志保存时的日志类型与 logSubType 一致性校验 */
class MonitorLogSaveValidateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MonitorLogService service() {
        List<MonitorLogSaveHandler> handlers =
                List.of(
                        new OperationLogSaveHandler(objectMapper),
                        new MessageLogSaveHandler(objectMapper),
                        new SystemLogSaveHandler(objectMapper));
        // 校验发生在落库之前，数据源不会被真正触达，故用 mock 顶替
        return new MonitorLogService(
                mock(JooqDataContextFactory.class),
                objectMapper,
                mock(MonitorOperateIndexService.class),
                mock(MonitorLogEnrichService.class),
                handlers);
    }

    private MonitorLogSaveReq operationReq(MonitorContentBase content) {
        MonitorLogSaveReq req = new MonitorLogSaveReq();
        req.setType(0);
        req.setContent(content);
        return req;
    }

    /** 断言请求没有被 logSubType 校验拦下（通过校验后会继续走到落库，mock 数据源必然抛错，故只校验异常类型） */
    private void assertNotRejectedBySubTypeCheck(MonitorLogSaveReq req) {
        Throwable ex = assertThrows(Throwable.class, () -> service().saveLog(req));
        assertTrue(
                !(ex instanceof BusinessException), "不应因 logSubType 校验被拒绝，实际异常：" + ex.getMessage());
    }

    @Test
    @DisplayName("操作日志漏传 content.logSubType 时拒绝保存")
    void shouldRejectMissingContentSubType() {
        MonitorContentBase content = new MonitorContentBase();
        content.setModuleName("外部对象");

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> service().saveLog(operationReq(content)));
        assertTrue(ex.getMessage().contains("logSubType"), ex.getMessage());
    }

    @Test
    @DisplayName("操作日志 content 子类型传成 messageLog 时拒绝保存")
    void shouldRejectMismatchedContentSubType() {
        MonitorContentMessage content = new MonitorContentMessage();
        content.setLogSubType("messageLog");
        content.setContent("消息正文");

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> service().saveLog(operationReq(content)));
        assertTrue(ex.getMessage().contains("operationLog"), ex.getMessage());
    }

    @Test
    @DisplayName("操作日志 approve 漏传 logSubType 时拒绝保存")
    void shouldRejectMissingApproveSubType() {
        MonitorLogSaveReq req = operationReq(null);
        req.setApprove(new MonitorApproveBase());

        assertThrows(BusinessException.class, () -> service().saveLog(req));
    }

    @Test
    @DisplayName("操作日志 content 带上 operationLog 子类型时通过校验")
    void shouldAcceptOperationLogWithSubType() {
        MonitorContentOperation content = new MonitorContentOperation();
        content.setLogSubType("operationLog");
        content.setResult("成功");

        assertNotRejectedBySubTypeCheck(operationReq(content));
    }

    @Test
    @DisplayName("系统日志只有共用字段，不要求 logSubType")
    void shouldAllowSystemLogWithoutSubType() {
        MonitorLogSaveReq req = new MonitorLogSaveReq();
        req.setType(2);
        MonitorContentBase content = new MonitorContentBase();
        content.setModuleName("系统");
        req.setContent(content);

        assertNotRejectedBySubTypeCheck(req);
    }
}
