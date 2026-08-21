package com.jdec.platform.config.biz.engine.controller;

import com.jdec.platform.config.api.engine.DataEngineApi;
import com.jdec.platform.config.api.engine.dto.request.DataQueryReq;
import com.jdec.platform.config.api.engine.dto.request.DataUpdateReq;
import com.jdec.platform.config.api.engine.dto.response.DataDetailResp;
import com.jdec.platform.config.api.engine.dto.response.DataPageResp;
import com.jdec.platform.config.biz.engine.strategy.DetailDataEngineStrategy;
import com.jdec.platform.config.biz.engine.strategy.ListDataEngineStrategy;
import com.jdec.platform.config.biz.engine.strategy.UpdateDataEngineStrategy;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "数据引擎", description = "基于模块配置动态生成的数据操作接口")
public class DataEngineController implements DataEngineApi {

    private final ListDataEngineStrategy listDataEngineStrategy;
    private final DetailDataEngineStrategy detailDataEngineStrategy;
    private final UpdateDataEngineStrategy updateDataEngineStrategy;

    @Override
    @PostMapping("/{moduleCode}/list")
    @Operation(summary = "动态数据列表查询")
    public DataPageResp listData(
            @PathVariable String moduleCode, @RequestBody DataQueryReq request) {
        return listDataEngineStrategy.execute(moduleCode, request);
    }

    @PostMapping("/{moduleCode}/list/wrapper")
    @Operation(summary = "动态数据列表查询(包装版)")
    public ApiResponse<DataPageResp> listDataWrapper(
            @PathVariable String moduleCode, @RequestBody DataQueryReq request) {
        return ApiResponse.success(listData(moduleCode, request));
    }

    @Override
    @GetMapping("/{moduleCode}/{id}")
    @Operation(summary = "动态数据详情查询")
    public DataDetailResp getDataDetail(@PathVariable String moduleCode, @PathVariable Long id) {
        return detailDataEngineStrategy.execute(moduleCode, id);
    }

    @GetMapping("/{moduleCode}/{id}/wrapper")
    @Operation(summary = "动态数据详情查询(包装版)")
    public ApiResponse<DataDetailResp> getDataDetailWrapper(
            @PathVariable String moduleCode, @PathVariable Long id) {
        return ApiResponse.success(getDataDetail(moduleCode, id));
    }

    @Override
    @PutMapping("/{moduleCode}/{id}")
    @Operation(summary = "动态数据更新")
    public void updateData(
            @PathVariable String moduleCode,
            @PathVariable Long id,
            @RequestBody DataUpdateReq request) {
        updateDataEngineStrategy.executeWithId(moduleCode, id, request);
    }

    @PutMapping("/{moduleCode}/{id}/wrapper")
    @Operation(summary = "动态数据更新(包装版)")
    public ApiResponse<String> updateDataWrapper(
            @PathVariable String moduleCode,
            @PathVariable Long id,
            @RequestBody DataUpdateReq request) {
        updateData(moduleCode, id, request);
        return ApiResponse.success("更新成功");
    }
}
