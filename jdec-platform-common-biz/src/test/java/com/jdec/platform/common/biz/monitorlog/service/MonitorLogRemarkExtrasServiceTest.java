package com.jdec.platform.common.biz.monitorlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.dto.common.FieldColumnExtrasDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 操作日志查询补充字段扩展信息测试：cloud 与 businessTypeCode 按当前配置实时补齐。 */
class MonitorLogRemarkExtrasServiceTest {

    private final ConfigApi configApi = mock(ConfigApi.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MonitorLogRemarkExtrasService service =
            new MonitorLogRemarkExtrasService(configApi, objectMapper);

    /** 词云字段补 cloud=1，配置业务类型的字段补 businessTypeCode，其余补 cloud=0 且不输出 businessTypeCode */
    @Test
    void fillColumnExtrasShouldFillCloudAndBusinessTypeCode() throws Exception {
        when(configApi.getFieldColumnExtras(any(), any()))
                .thenReturn(
                        List.of(
                                FieldColumnExtrasDTO.builder()
                                        .tableName("member")
                                        .columnName("status")
                                        .relationBusinessNo("GX1")
                                        .businessTypeCode("wordCloudLeaf")
                                        .build(),
                                FieldColumnExtrasDTO.builder()
                                        .tableName("basic_info")
                                        .columnName("logo_url")
                                        .businessTypeCode("image")
                                        .build()));

        JsonNode columns = columnsOf(service.fillColumnExtras(readRemark()));

        assertEquals(1, columns.get(0).path("cloud").asInt());
        assertEquals("wordCloudLeaf", columns.get(0).path("businessTypeCode").asText());
        assertEquals(0, columns.get(1).path("cloud").asInt());
        assertEquals("image", columns.get(1).path("businessTypeCode").asText());
        assertEquals(0, columns.get(2).path("cloud").asInt());
        assertFalse(columns.get(2).has("businessTypeCode"));
    }

    /** 历史日志已落库的扩展信息按当前配置覆盖：配置缺失时移除 businessTypeCode */
    @Test
    void fillColumnExtrasShouldOverwriteStoredExtras() throws Exception {
        when(configApi.getFieldColumnExtras(any(), any())).thenReturn(List.of());

        JsonNode columns = columnsOf(service.fillColumnExtras(readRemark()));

        assertEquals(0, columns.get(0).path("cloud").asInt());
        assertFalse(columns.get(0).has("businessTypeCode"));
    }

    /** 配置查询失败：保持 remark 原样返回，不把已有扩展信息覆盖成默认值 */
    @Test
    void fillColumnExtrasShouldKeepRemarkWhenConfigQueryFails() throws Exception {
        when(configApi.getFieldColumnExtras(any(), any()))
                .thenThrow(new RuntimeException("config center unavailable"));

        Object remark = readRemark();

        assertSame(remark, service.fillColumnExtras(remark));
    }

    private Object readRemark() throws Exception {
        String json =
                """
                {"change":[{"actions":{"u":[{"columns":[
                  {"name":"状态","field":"member.status","cloud":0,"businessTypeCode":"oldCode"},
                  {"name":"LOGO","field":"basic_info.logo_url"},
                  {"name":"手机号","field":"member.mobile"}
                ]}]}}]}
                """;
        return objectMapper.readValue(json, Object.class);
    }

    private JsonNode columnsOf(Object remark) {
        JsonNode root = objectMapper.valueToTree(remark);
        return root.path("change").get(0).path("actions").path("u").get(0).path("columns");
    }
}
