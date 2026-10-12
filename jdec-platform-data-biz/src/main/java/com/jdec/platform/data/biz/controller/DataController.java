package com.jdec.platform.data.biz.controller;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TableOptionReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通用低代码数据引擎控制器 提供跨业务模块的通用动态读写能力
 *
 * <p>此处只做成功响应包装：异常统一交由 {@code GlobalExceptionHandler} 记录堆栈并返回 500， 避免每个端点重复 try/catch/log/rethrow。
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "通用低代码数据引擎接口", description = "提供通用的动态查询、详情加载与自相似树形同构保存")
public class DataController {

    private final DataApi dataApi;

    @PostMapping("/table/query")
    @Operation(summary = "物理表驱动动态分页查询", description = "支持直接使用物理表名、列名规格及多条件过滤进行跨自相似树查询 (脱离 fieldId)")
    public ApiResponse<DataPage<DataBlock>> queryByTable(@RequestBody TablePageQueryReq req) {
        return ApiResponse.success(dataApi.queryByTable(req));
    }

    @PostMapping("/table/detail")
    @Operation(summary = "物理表驱动单条详情查询", description = "支持直接根据物理表名及列名规约加载单条业务对象详情，返回读写同构的 DataBlock")
    public ApiResponse<DataBlock> getDetailByTable(@RequestBody TableDetailQueryReq req) {
        return ApiResponse.success(dataApi.getDetailByTable(req));
    }

    @PostMapping("/table/count")
    @Operation(summary = "物理表驱动动态计数查询", description = "返回满足基于物理表/列过滤条件的记录总数 (脱离 fieldId)")
    public ApiResponse<Long> countByTable(@RequestBody TableQueryReq req) {
        return ApiResponse.success(dataApi.countByTable(req));
    }

    @PostMapping("/table/options")
    @Operation(summary = "物理表驱动字段搜索下拉候选项", description = "支持直接根据物理表名及列名获取去重候选值列表 (脱离 fieldId)")
    public ApiResponse<List<DataOptionResp>> getOptionsByTable(@RequestBody TableOptionReq req) {
        return ApiResponse.success(dataApi.getOptionsByTable(req));
    }

    @GetMapping("/{moduleId}/{id}/full")
    @Operation(
            summary = "通用单条全量子孙树详情查询 (GET 快捷全量物理树)",
            description = "自动递归抓取当前模块及所有伴生表、全部子孙物理表的全部列信息，返回完整 DataBlock")
    public ApiResponse<DataBlock> getFullDetail(
            @Parameter(description = "主模块 ID", example = "1001") @PathVariable Long moduleId,
            @Parameter(description = "单据主键 ID", example = "100") @PathVariable Long id) {
        return ApiResponse.success(dataApi.getFullDetail(moduleId, id));
    }

    @PostMapping("/save")
    @Operation(
            summary = "通用同构原子保存",
            description = "入参结构与 getDetail 响应的 DataBlock 100% 读写同构，直接提交 DataBlock，返回 DataSaveResp")
    public ApiResponse<DataSaveResp> save(@RequestBody DataBlock block) {
        return ApiResponse.success(dataApi.save(block));
    }

    @PostMapping("/table/fork-version")
    @Operation(
            summary = "单据版本派生与局部增量融合保存",
            description = "基于基准单据ID(如156)自动抓取完整数据树，并与前端提交的局部增量修改(patchData)深度合并，生成全新的独立单据版本并返回新根ID")
    public ApiResponse<Long> forkNewVersion(@RequestBody ForkVersionReq req) {
        return ApiResponse.success(dataApi.forkNewVersion(req));
    }
}
