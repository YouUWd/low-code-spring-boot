package com.jdec.platform.common_business.biz.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.third.WordApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/word")
@RequiredArgsConstructor
@Tag(name = "Word 外部服务", description = "Word 外部 API 的关系查询接口")
public class WordApiController {

    private final WordApiClient wordApiClient;

    @GetMapping("/relation/info")
    @Operation(summary = "获取关系详情")
    public ApiResponse<Object> getRelationInfo(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationInfo(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/info/tree")
    @Operation(summary = "获取关系详情树")
    public ApiResponse<Object> getRelationInfoTree(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationInfoTree(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/word")
    @Operation(summary = "获取关系值")
    public ApiResponse<Object> getRelationWord(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationWord(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }
}
