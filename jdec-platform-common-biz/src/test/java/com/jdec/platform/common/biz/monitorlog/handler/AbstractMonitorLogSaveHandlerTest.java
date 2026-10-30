package com.jdec.platform.common.biz.monitorlog.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 监控日志保存处理器测试：remark 落库前统一归一化为 JSON 结构。 */
class AbstractMonitorLogSaveHandlerTest {

    private final AbstractMonitorLogSaveHandler handler =
            new AbstractMonitorLogSaveHandler(new ObjectMapper()) {
                @Override
                public Integer supportType() {
                    return 0;
                }
            };

    /** 传入 JSON 文本：落库为 JSON 对象，不多套一层引号转义 */
    @Test
    void buildEntityShouldNormalizeJsonTextRemark() {
        MonitorLogSaveReq req = remarkReq("{\"change\":[{\"name\":\"【W001】\"}]}");

        assertEquals(
                "{\"change\":[{\"name\":\"【W001】\"}]}", handler.buildEntity(req).getDescription());
    }

    /** 传入对象：按对象序列化 */
    @Test
    void buildEntityShouldSerializeObjectRemark() {
        MonitorLogSaveReq req = remarkReq(Map.of("comment", "同意"));

        assertEquals("{\"comment\":\"同意\"}", handler.buildEntity(req).getDescription());
    }

    /** 传入普通文本：原样作为 JSON 字符串保存 */
    @Test
    void buildEntityShouldKeepPlainTextRemark() {
        MonitorLogSaveReq req = remarkReq("同意");

        assertEquals("\"同意\"", handler.buildEntity(req).getDescription());
    }

    /** 形似 JSON 但格式非法：按原始文本保存，归一化失败不丢失内容 */
    @Test
    void buildEntityShouldKeepInvalidJsonTextRemark() {
        MonitorLogSaveReq req = remarkReq("{不是JSON}");

        assertEquals("\"{不是JSON}\"", handler.buildEntity(req).getDescription());
    }

    private static MonitorLogSaveReq remarkReq(Object remark) {
        MonitorLogSaveReq req = new MonitorLogSaveReq();
        req.setType(0);
        req.setRemark(remark);
        return req;
    }
}
