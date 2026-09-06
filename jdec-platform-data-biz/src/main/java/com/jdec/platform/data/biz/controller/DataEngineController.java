package com.jdec.platform.data.biz.controller;

import com.jdec.platform.data.api.DataEngineApi;
import com.jdec.platform.data.api.dto.request.BatchDynamicQueryReq;
import com.jdec.platform.data.api.dto.request.BatchDynamicSaveReq;
import com.jdec.platform.data.api.dto.request.DynamicDetailReq;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.response.BatchEngineDataResult;
import com.jdec.platform.data.api.dto.response.BatchSaveResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
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
@Tag(name = "通用低代码数据引擎接口", description = "提供通用的动态查询、详情加载、同构保存与批量事务落库")
public class DataEngineController {

    private final DataEngineApi dataEngineApi;

    @PostMapping("/query")
    @Operation(summary = "通用动态分页查询", description = "支持任意模块的分页、排序与组合过滤")
    public ApiResponse<EngineDataResult<DataPage<Map<String, Object>>>> query(
            @RequestBody DynamicQueryReq req) {
        try {
            EngineDataResult<DataPage<Map<String, Object>>> result = dataEngineApi.query(req);
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

    @PostMapping("/detail")
    @Operation(summary = "通用单条详情查询", description = "支持任意模块的单条业务对象详情加载")
    public ApiResponse<EngineDataResult<Map<String, Object>>> getDetail(
            @RequestBody DynamicDetailReq req) {
        try {
            EngineDataResult<Map<String, Object>> result = dataEngineApi.getDetail(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getDetail failed: moduleId={}, id={}, err={}",
                    req.getModuleId(),
                    req.getId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/batch-query")
    @Operation(summary = "多模块并发批量查询", description = "用于多Tab或仪表盘一次性并发拉取")
    public ApiResponse<BatchEngineDataResult> batchQuery(@RequestBody BatchDynamicQueryReq req) {
        try {
            BatchEngineDataResult result = dataEngineApi.batchQuery(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error("Data engine batchQuery failed: err={}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(summary = "通用同构原子保存", description = "扁平 tables 结构单模块跨表原子事务保存")
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

    @PostMapping("/batch-save")
    @Operation(summary = "多模块跨表原子批量保存", description = "跨模块原子事务强一致性落库与外键自动传播")
    public ApiResponse<BatchSaveResp> batchSave(@RequestBody BatchDynamicSaveReq req) {
        try {
            BatchSaveResp resp = dataEngineApi.batchSave(req);
            return ApiResponse.success(resp);
        } catch (Exception ex) {
            log.error("Data engine batchSave failed: err={}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
