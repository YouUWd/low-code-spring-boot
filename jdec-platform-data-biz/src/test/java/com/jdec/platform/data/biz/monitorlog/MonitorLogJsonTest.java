package com.jdec.platform.data.biz.monitorlog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jdec.platform.data.api.dto.log.MonitorApproveBase;
import com.jdec.platform.data.api.dto.log.MonitorApproveOperation;
import com.jdec.platform.data.api.dto.log.MonitorContentBase;
import com.jdec.platform.data.api.dto.log.MonitorContentMessage;
import com.jdec.platform.data.api.dto.log.MonitorContentOperation;
import com.jdec.platform.data.api.dto.request.MonitorLogSaveReq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 监控日志 approve / content 的基类-子类 JSON 多态读写验证 */
class MonitorLogJsonTest {

    /** 仅用于验证多态契约，日期格式由 JacksonConfig 统一配置，此处按 ISO-8601 传值 */
    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("操作日志 content 按 logSubType 反序列化为子类并保留独有字段")
    void shouldDeserializeOperationContent() throws Exception {
        String json =
                """
                {"logSubType":"operationLog","moduleName":"外部对象","linkName":"外部对象列表",
                 "version":1,"operation":"提交","result":"成功","fontColor":"#00A870"}
                """;

        MonitorContentBase content = objectMapper.readValue(json, MonitorContentBase.class);

        MonitorContentOperation operation =
                assertInstanceOf(MonitorContentOperation.class, content);
        assertEquals("外部对象", operation.getModuleName());
        assertEquals("提交", operation.getOperation());
        assertEquals("成功", operation.getResult());
        assertEquals("#00A870", operation.getFontColor());
    }

    @Test
    @DisplayName("消息日志 content 按 logSubType 反序列化为子类")
    void shouldDeserializeMessageContent() throws Exception {
        String json =
                "{\"logSubType\":\"messageLog\",\"moduleName\":\"待办\",\"content\":\"您有新的待办\"}";

        MonitorContentBase content = objectMapper.readValue(json, MonitorContentBase.class);

        MonitorContentMessage message = assertInstanceOf(MonitorContentMessage.class, content);
        assertEquals("待办", message.getModuleName());
        assertEquals("您有新的待办", message.getContent());
        assertNull(message.getOperation());
    }

    @Test
    @DisplayName("未带 logSubType 的 content 按基类处理")
    void shouldFallbackToBaseContent() throws Exception {
        String json = "{\"moduleName\":\"系统\",\"operation\":\"登录\"}";

        assertInstanceOf(
                MonitorContentBase.class, objectMapper.readValue(json, MonitorContentBase.class));
    }

    @Test
    @DisplayName("approve 按 logSubType 反序列化为操作日志子类")
    void shouldDeserializeOperationApprove() throws Exception {
        String json =
                """
                {"logSubType":"operationLog","name":"提交审批","path":"icon-submit",
                 "result":"通过","type":1}
                """;

        MonitorApproveBase approve = objectMapper.readValue(json, MonitorApproveBase.class);

        MonitorApproveOperation operation =
                assertInstanceOf(MonitorApproveOperation.class, approve);
        assertEquals("提交审批", operation.getName());
        assertEquals("通过", operation.getResult());
        assertEquals(1, operation.getType());
    }

    @Test
    @DisplayName("子类序列化写入 logSubType，再读回仍是同一子类（落库-回显 闭环）")
    void shouldRoundTripSubclass() throws Exception {
        MonitorContentOperation content = new MonitorContentOperation();
        content.setLogSubType("operationLog");
        content.setModuleName("外部对象");
        content.setOperation("提交");
        content.setResult("成功");

        String json = objectMapper.writeValueAsString(content);
        assertTrue(json.contains("\"logSubType\":\"operationLog\""), json);
        assertTrue(json.contains("\"moduleName\":\"外部对象\""), json);

        MonitorContentBase parsed = objectMapper.readValue(json, MonitorContentBase.class);
        MonitorContentOperation replayed = assertInstanceOf(MonitorContentOperation.class, parsed);
        assertEquals("外部对象", replayed.getModuleName());
        assertEquals("成功", replayed.getResult());
        assertNull(replayed.getFontColor());
    }

    @Test
    @DisplayName("整条保存请求可解析 approve 与 content 各自的多态子类")
    void shouldDeserializeFullSaveReq() throws Exception {
        String json =
                """
                {"type":0,"sysType":"operationLog","subjectId":1,"subjectName":"公司",
                 "loginInfo":{"userId":9,"userName":"张三","avatar":"a.png","userNumber":"A001",
                              "roleId":2,"roleName":"管理员","deptId":3,"deptName":"技术部",
                              "jobId":4,"jobName":"工程师"},
                 "mockInfo":{"flag":0,"userId":9,"name":"张三","avatar":"a.png","userNumber":"A001"},
                 "approve":{"logSubType":"operationLog","name":"提交","path":"icon","result":"通过","type":1},
                 "content":{"logSubType":"operationLog","moduleName":"外部对象","linkName":"外部对象列表",
                            "version":1,"operation":"提交","result":"成功","fontColor":"#00A870"},
                 "ip":"127.0.0.1","moduleId":1001,"dataId":88,"browser":"Chrome","platForm":"PC",
                 "remark":{"userIds":[1,2]},"recordTime":"2026-09-17T10:00:00"}
                """;

        MonitorLogSaveReq req = objectMapper.readValue(json, MonitorLogSaveReq.class);

        assertEquals(0, req.getType());
        assertEquals(9L, req.getLoginInfo().getUserId());
        assertEquals("技术部", req.getLoginInfo().getDeptName());
        assertInstanceOf(MonitorApproveOperation.class, req.getApprove());
        assertInstanceOf(MonitorContentOperation.class, req.getContent());
        assertEquals("外部对象列表", req.getContent().getLinkName());
    }
}
