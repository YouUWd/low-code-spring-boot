package com.jdec.platform.data.biz.controller;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
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
public class DataController {

    private final DataApi dataApi;

    @PostMapping("/query")
    @Operation(summary = "通用动态分页查询", description = "支持任意自相似模块树的分页、排序与组合过滤")
    public ApiResponse<DataPage<Map<String, Object>>> query(@RequestBody QueryReq req) {
        try {
            DataPage<Map<String, Object>> result = dataApi.query(req);
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
    public ApiResponse<List<DataOption>> getOptions(@RequestBody OptionReq req) {
        try {
            List<DataOption> result = dataApi.getOptions(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getOptions failed: moduleId={}, fieldId={}, err={}",
                    req.getModuleId(),
                    req.getFieldId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/{moduleId}/{id}")
    @Operation(summary = "通用单条详情查询", description = "精准根据模块 ID 与单据 ID 加载单条业务对象详情，支持通过 fields 指定投影字段")
    public ApiResponse<DataDetail<Map<String, Object>>> getDetail(
            @PathVariable Long moduleId,
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> fields) {
        try {
            DataDetail<Map<String, Object>> result = dataApi.getDetail(moduleId, id, fields);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getDetail failed: moduleId={}, id={}, fields={}, err={}",
                    moduleId,
                    id,
                    fields,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(
            summary = "通用同构原子保存",
            description = "与 detail 响应的 data 结构 100% 读写同构，直接提交 detail.data 载荷，返回各模块主键映射")
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> data) {
        try {
            SaveReq req = SaveReq.builder().records(List.of(data)).build();
            Map<String, Object> ids = dataApi.saveAndCollectIds(req);
            return ApiResponse.success(ids);
        } catch (Exception ex) {
            log.error("Data engine save failed: data={}, err={}", data, ex.getMessage(), ex);
            throw ex;
        }
    }
}
