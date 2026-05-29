package com.jdec.platform.config.biz.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.third.WordApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Word 外部服务 REST 控制器
 *
 * <p>透传 Word 外部 API 的调用，提供关系查询、绑定/取消绑定等接口。
 */
@RestController
@RequestMapping("/api/config/word")
@RequiredArgsConstructor
@Tag(name = "Word 外部服务", description = "Word 外部 API 的关系查询、绑定/取消绑定接口")
public class WordApiController {

    private final WordApiClient wordApiClient;

    @GetMapping("/relation/all")
    @Operation(summary = "获取所有关系")
    public ApiResponse<Object> getAllRelation() {
        Object data = wordApiClient.getAllRelation(new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/info/tree")
    @Operation(summary = "获取关系详情")
    public ApiResponse<Object> getRelationInfoTree(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationInfoTree(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/world")
    @Operation(summary = "获取关系词列表")
    public ApiResponse<Object> getRelationWorld(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationWord(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @PostMapping("/relation/bindOrCancel")
    @Operation(summary = "绑定或取消绑定")
    public ApiResponse<Void> bindOrCancel(@RequestBody Map<String, Object> params) {
        wordApiClient.getBindOrCancel(params);
        return ApiResponse.success();
    }
}
