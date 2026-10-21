package com.jdec.platform.common.biz.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.third.WordApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

/**
 * Word 外部服务统一 REST 控制器
 *
 * <p>提供关系查询、详情树查询、关系词提取以及绑定/取消绑定等完整能力。 同时映射 {@code /api/common/word} 与 {@code /api/config/word}
 * 保证向前兼容。
 */
@RestController
@RequestMapping({"/api/common/word", "/api/config/word"})
@Tag(name = "Word 外部服务", description = "Word 外部 API 的关系查询、绑定/取消绑定接口")
public class WordApiController {

    private final WordApiClient wordApiClient;

    public WordApiController(WordApiClient wordApiClient) {
        this.wordApiClient = wordApiClient;
    }

    @GetMapping("/relation/all")
    @Operation(summary = "获取所有关系")
    public ApiResponse<JsonNode> getAllRelation() {
        JsonNode data = wordApiClient.getAllRelation(new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/info")
    @Operation(summary = "获取关系详情")
    public ApiResponse<JsonNode> getRelationInfo(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        JsonNode data = wordApiClient.getRelationInfo(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/info/tree")
    @Operation(summary = "获取关系详情树")
    public ApiResponse<JsonNode> getRelationInfoTree(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        JsonNode data = wordApiClient.getRelationInfoTree(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/word")
    @Operation(summary = "获取关系词列表")
    public ApiResponse<JsonNode> getRelationWord(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        JsonNode data = wordApiClient.getRelationWord(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @PostMapping("/relation/bindOrCancel")
    @Operation(summary = "绑定或取消绑定")
    public ApiResponse<Void> bindOrCancel(@RequestBody Map<String, Object> params) {
        wordApiClient.getBindOrCancel(params);
        return ApiResponse.success();
    }

    private static final Set<String> REMOVED_FIELDS = Set.of("id", "pid", "relation_no");

    @PostMapping("/relation/word/edit")
    @Operation(summary = "获取关系词编辑信息")
    public ApiResponse<JsonNode> getRelationWordEdit(@RequestBody Map<String, Object> params) {
        JsonNode data = wordApiClient.getRelationWordEdit(params, new TypeReference<>() {});
        removeFields(data);
        return ApiResponse.success(data);
    }

    /** 递归移除节点中的指定字段（如 id、pid、relation_no） */
    private void removeFields(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            for (String field : REMOVED_FIELDS) {
                objectNode.remove(field);
            }
            for (Iterator<JsonNode> it = objectNode.elements(); it.hasNext(); ) {
                removeFields(it.next());
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode item : arrayNode) {
                removeFields(item);
            }
        }
    }
}
