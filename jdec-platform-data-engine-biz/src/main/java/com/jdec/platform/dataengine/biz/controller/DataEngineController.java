package com.jdec.platform.dataengine.biz.controller;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
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

    //    @PostMapping("/menu/detail")
    @Operation(summary = "获取详情数据", description = "获取详情数据")
    public ApiResponse<NestedModuleDataDTO> getDetail(
            @RequestBody DynamicDetailQueryReq detailQueryReq) {
        return ApiResponse.success(dataEngineApi.getDetail(detailQueryReq));
    }

    //    @PostMapping("/menu/get-version-list")
    @Operation(summary = "获取版本列表数据", description = "获取版本列表数据")
    public ApiResponse<List<VersionListResp>> getVersionList(
            @RequestBody VersionListQueryReq versionListQueryReq) {
        return ApiResponse.success(dataEngineApi.getVersionList(versionListQueryReq));
    }

    @PostMapping("/save")
    @Operation(summary = "保存模块数据", description = "保存模块数据")
    public ApiResponse<Long> save(@RequestBody DynamicSaveReq req) {
        return ApiResponse.success(dataEngineApi.save(req));
    }

    @PostMapping("/insertAndGetId")
    @Operation(summary = "保存模块数据", description = "保存模块数据")
    public ApiResponse<Long> insertAndGetId(@RequestBody DynamicInsertReq req) {
        return ApiResponse.success(dataEngineApi.insertAndGetId(req));
    }

    @PostMapping("/deleteById")
    @Operation(summary = "保存模块数据", description = "保存模块数据")
    public ApiResponse<Long> deleteById(@RequestBody DynamicDeleteReq req) {
        return ApiResponse.success(dataEngineApi.deleteById(req));
    }

    @PostMapping("/process")
    @Operation(summary = "审批状态回调", description = "用于测试")
    public ApiResponse<Object> save(@RequestBody ApprovalStatusCallBackReq req) {
        return ApiResponse.success(dataEngineApi.processApprovalStatus(req));
    }
}
