package com.jdec.platform.shared.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntityRefTest {

    @Test
    @DisplayName("标准格式序列化与反序列化")
    void testStandardSerializationAndDeserialization() {
        EntityRef ref =
                EntityRef.builder()
                        .businessNo("CR2026102015072600001")
                        .versionNo(3)
                        .label("审批测试01")
                        .moduleCode("COMPANY-ROOT")
                        .id(100L)
                        .extra(Map.of("uscc", "91110000000000000X"))
                        .build();

        String json = ref.toJson();
        assertTrue(json.contains("\"business_no\":\"CR2026102015072600001\""));
        assertTrue(json.contains("\"version_no\":3"));
        assertTrue(json.contains("\"label\":\"审批测试01\""));
        assertTrue(json.contains("\"module_code\":\"COMPANY-ROOT\""));
        assertTrue(json.contains("\"id\":100"));
        assertTrue(json.contains("\"extra\":{\"uscc\":\"91110000000000000X\"}"));

        EntityRef parsed = EntityRef.from(json);
        assertNotNull(parsed);
        assertEquals("CR2026102015072600001", parsed.getBusinessNo());
        assertEquals(Integer.valueOf(3), parsed.getVersionNo());
        assertEquals("审批测试01", parsed.getLabel());
        assertEquals("COMPANY-ROOT", parsed.getModuleCode());
        assertEquals(Long.valueOf(100L), parsed.getId());
        assertTrue(parsed.isVersionLocked());
        assertEquals("jdec://COMPANY-ROOT/CR2026102015072600001@3", parsed.toUri());
    }

    @Test
    @DisplayName("平滑兼容旧版本格式（value / versionNo / name / extra_data）")
    void testLegacyFormatCompatibility() {
        // 场景1: 历史 info.parent_id 格式: {"value": "CR...", "label": "...", "version_no": 3,
        // "extra_data": {}}
        String legacyParentIdJson =
                "{\"value\":\"CR2026102015072600001\",\"label\":\"审批测试01\",\"version_no\":3,\"extra_data\":{\"taxNo\":\"12345\"}}";
        EntityRef fromParentId = EntityRef.from(legacyParentIdJson);
        assertNotNull(fromParentId);
        assertEquals("CR2026102015072600001", fromParentId.getBusinessNo());
        assertEquals(Integer.valueOf(3), fromParentId.getVersionNo());
        assertEquals("审批测试01", fromParentId.getLabel());
        assertEquals("12345", fromParentId.safeExtra().get("taxNo"));

        // 场景2: 历史 venue_line.company_name 格式: {"id": 100, "name": "某焦化公司", "versionNo": 2}
        String legacyVenueLineJson = "{\"id\":100,\"name\":\"某焦化公司\",\"versionNo\":2}";
        EntityRef fromVenueLine = EntityRef.from(legacyVenueLineJson);
        assertNotNull(fromVenueLine);
        assertEquals(Long.valueOf(100L), fromVenueLine.getId());
        assertEquals("某焦化公司", fromVenueLine.getLabel());
        assertEquals(Integer.valueOf(2), fromVenueLine.getVersionNo());
    }

    @Test
    @DisplayName("动态未锁版引用（version_no 为 null）生成最新版 URI")
    void testDynamicLatestReference() {
        EntityRef dynamicRef = EntityRef.of("COMPANY-ROOT", "CR2026102015072600001", null, "动态公司");
        assertFalse(dynamicRef.isVersionLocked());
        assertEquals("jdec://COMPANY-ROOT/CR2026102015072600001@latest", dynamicRef.toUri());
    }

    @Test
    @DisplayName("容错处理：非法/空字符串输入安全返回 null")
    void testFaultTolerance() {
        assertNull(EntityRef.from(null));
        assertNull(EntityRef.from(""));
        assertNull(EntityRef.from("   "));
        assertNull(EntityRef.from("null"));
        assertNull(EntityRef.from("{}"));
        assertNull(EntityRef.from("[]"));
        assertNull(EntityRef.from("{invalid json"));
    }
}
