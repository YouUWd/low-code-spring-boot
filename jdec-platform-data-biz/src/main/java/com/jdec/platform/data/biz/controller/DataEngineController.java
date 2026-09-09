package com.jdec.platform.data.biz.controller;

import com.jdec.platform.data.api.DataEngineApi;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.request.EngineHeaderReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.api.dto.response.EngineHeaderResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 通用低代码数据引擎控制器 提供跨业务模块的通用动态读写能力 */
@Slf4j
@RestController
@RequestMapping("/api/data/engine")
@RequiredArgsConstructor
@Tag(name = "通用低代码数据引擎接口", description = "提供通用的动态查询、详情加载与自相似树形同构保存")
public class DataEngineController {

    private final DataEngineApi dataEngineApi;

    @PostMapping("/header")
    @Operation(summary = "通用动态表头配置查询", description = "获取指定字段或模块的表头元数据配置（动静分离）")
    public ApiResponse<EngineHeaderResp> getHeader(@RequestBody EngineHeaderReq req) {
        try {
            EngineHeaderResp result = dataEngineApi.getHeader(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error("Data engine getHeader failed: err={}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/query")
    @Operation(summary = "通用动态分页查询", description = "支持任意自相似模块树的分页、排序与组合过滤")
    public ApiResponse<DataPage<Map<String, Object>>> query(@RequestBody DynamicQueryReq req) {
        try {
            DataPage<Map<String, Object>> result = dataEngineApi.query(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine query failed: moduleId={}, err={}",
                    req.getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/options")
    @Operation(summary = "通用字段搜索下拉候选项", description = "获取当前模块内指定表与字段的去重候选值列表，返回 label-value 结构")
    public ApiResponse<List<DynamicOptionItem>> getOptions(@RequestBody DynamicOptionReq req) {
        try {
            List<DynamicOptionItem> result = dataEngineApi.getOptions(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getOptions failed: moduleId={}, table={}, column={}, err={}",
                    req.getModuleId(),
                    req.getTableName(),
                    req.getColumnName(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/{moduleId}/{id}")
    @Operation(summary = "通用单条详情查询", description = "精准根据模块 ID 与单据 ID 加载单条业务对象详情")
    public ApiResponse<EngineDataResult<Map<String, Object>>> getDetail(
            @PathVariable Long moduleId, @PathVariable Long id) {
        try {
            EngineDataResult<Map<String, Object>> result = dataEngineApi.getDetail(moduleId, id);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getDetail failed: moduleId={}, id={}, err={}",
                    moduleId,
                    id,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(summary = "通用同构原子保存", description = "支持自相似嵌套树级联保存与外键自动传播")
    public ApiResponse<Map<String, Object>> save(@RequestBody DynamicSaveReq req) {
        try {
            Long id = dataEngineApi.save(req);
            return ApiResponse.success(Map.of("id", id, "masterId", id));
        } catch (Exception ex) {
            log.error(
                    "Data engine save failed: moduleId={}, err={}",
                    req.getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
