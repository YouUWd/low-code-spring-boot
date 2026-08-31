package com.jdec.platform.data.biz.snapshot;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 快照差异比对引擎 负责比对两个版本的 JSON 快照生成结构化差异列表 */
@Slf4j
@Component
public class SnapshotDiffEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 比对两个快照 JSON 字符串 */
    public List<Map<String, Object>> diff(String oldJson, String newJson) {
        List<Map<String, Object>> diffList = new ArrayList<>();
        if (oldJson == null || oldJson.isBlank()) {
            Map<String, Object> initChange = new HashMap<>();
            initChange.put("op", "CREATE");
            initChange.put("remark", "初始版本创建");
            diffList.add(initChange);
            return diffList;
        }

        try {
            JsonNode oldNode = objectMapper.readTree(oldJson);
            JsonNode newNode = objectMapper.readTree(newJson);
            JsonNode patch = JsonDiff.asJson(oldNode, newNode);

            for (JsonNode item : patch) {
                Map<String, Object> diffItem = new HashMap<>();
                diffItem.put("op", item.path("op").asText());
                diffItem.put("path", item.path("path").asText());
                diffItem.put("value", item.path("value").toString());
                diffList.add(diffItem);
            }
        } catch (Exception e) {
            log.error("比对快照差异异常: ", e);
        }

        return diffList;
    }

    /** 将差异列表序列化为 JSON 字符串 */
    public String diffToJson(String oldJson, String newJson) {
        List<Map<String, Object>> list = diff(oldJson, newJson);
        return JSON.toJSONString(list);
    }
}
