package com.jdec.platform.shared.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** JSON 值工具测试：结构化文本归一化。 */
class JsonValueUtilsTest {

    /** 对象文本：解析为对象节点，供落库/下发时按对象序列化 */
    @Test
    void parseStructuredTextShouldParseObjectText() {
        Object value = JsonValueUtils.parseStructuredText(" {\"comment\":\"同意\"} ");

        JsonNode node = assertInstanceOf(JsonNode.class, value);
        assertEquals("同意", node.get("comment").asText());
    }

    /** 数组文本：解析为数组节点 */
    @Test
    void parseStructuredTextShouldParseArrayText() {
        Object value = JsonValueUtils.parseStructuredText("[{\"name\":\"W001\"}]");

        JsonNode node = assertInstanceOf(JsonNode.class, value);
        assertEquals(1, node.size());
    }

    /** 普通文本：原样返回，不强行解析为 JSON */
    @Test
    void parseStructuredTextShouldKeepPlainText() {
        assertEquals("同意", JsonValueUtils.parseStructuredText("同意"));
    }

    /** 形似 JSON 但格式非法：原样返回，避免丢失内容 */
    @Test
    void parseStructuredTextShouldKeepInvalidJsonText() {
        assertEquals("{不是JSON}", JsonValueUtils.parseStructuredText("{不是JSON}"));
    }

    /** 非文本值：原样返回 */
    @Test
    void parseStructuredTextShouldKeepNonTextValue() {
        Object value = Map.of("comment", "同意");

        assertSame(value, JsonValueUtils.parseStructuredText(value));
    }
}
