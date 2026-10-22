package com.jdec.platform.dataengine.biz.controller;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-engine/v1/modules")
@RequiredArgsConstructor
@Tag(
        name = "数据引擎", // 分组名称（文档中显示的分组标签）
        description = "动态数据查询和保存" // 分组描述
        )
public class DataEngineController {

    private final DataEngineApi dataEngineApi;

    @PostMapping("/save")
    @Operation(summary = "保存模块数据", description = "保存模块数据")
    public ApiResponse<Long> save(@RequestBody DynamicSaveReq req) {
        return ApiResponse.success(dataEngineApi.save(req).getDataId());
    }

    @PostMapping("/process")
    @Operation(summary = "审批状态回调", description = "用于测试")
    public ApiResponse<Object> save(@RequestBody ApprovalStatusCallBackReq req) {
        return ApiResponse.success(dataEngineApi.processApprovalStatus(req));
    }
}
