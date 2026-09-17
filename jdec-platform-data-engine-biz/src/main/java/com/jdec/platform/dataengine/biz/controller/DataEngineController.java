package com.jdec.platform.dataengine.biz.controller;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DetailDataResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

    @PostMapping("/{moduleId}/query")
    @Operation(summary = "获取列表数据", description = "获取列表数据")
    public ApiResponse<DynamicQueryResp> query(
            @PathVariable Long moduleId, @RequestBody DynamicQueryReq req) {
        req.setModuleId(moduleId);
        return ApiResponse.success(dataEngineApi.query(req));
    }

    @GetMapping("/menu/detail/{id}")
    @Operation(summary = "获取详情数据", description = "获取详情数据")
    public ApiResponse<DetailDataResp> getDetail(
            @PathVariable Long id,
            Long moduleId,
            String btnType,
            @RequestParam(required = false) List<Long> filterFields) {
        return ApiResponse.success(dataEngineApi.getDetail(moduleId, id, btnType, filterFields));
    }

    @PostMapping("/save")
    @Operation(summary = "保存模块数据", description = "保存模块数据")
    public ApiResponse<Long> save(@RequestBody DynamicSaveReq req) {
        return ApiResponse.success(dataEngineApi.save(req));
    }
}
