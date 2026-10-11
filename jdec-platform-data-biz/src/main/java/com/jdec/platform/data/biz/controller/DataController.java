package com.jdec.platform.data.biz.controller;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.SaveResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 通用低代码数据引擎控制器 提供跨业务模块的通用动态读写能力 */
@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "通用低代码数据引擎接口", description = "提供通用的动态查询、详情加载与自相似树形同构保存")
public class DataController {

    private final DataApi dataApi;

    @Deprecated
    @PostMapping("/query")
    @Operation(
            summary = "通用动态分页查询 (将废弃)",
            description = "旧版基于 fieldId，计划废弃，请迁移至 /api/data/table/query")
    public ApiResponse<DataPage<DataBlock>> query(@RequestBody PageQueryReq req) {
        try {
            DataPage<DataBlock> result = dataApi.query(req);
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

    @Deprecated
    @PostMapping("/count")
    @Operation(
            summary = "通用动态计数查询 (将废弃)",
            description = "旧版基于 fieldId，计划废弃，请迁移至 /api/data/table/count")
    public ApiResponse<Long> count(@RequestBody QueryReq req) {
        try {
            Long result = dataApi.count(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine count failed: moduleId={}, err={}",
                    req.getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @Deprecated
    @PostMapping("/options")
    @Operation(
            summary = "通用字段搜索下拉候选项 (将废弃)",
            description = "旧版基于 fieldId，计划废弃，请迁移至 /api/data/table/options")
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

    @Deprecated
    @PostMapping("/detail")
    @Operation(
            summary = "通用单条详情查询 (POST 树形投影/过滤) (将废弃)",
            description = "旧版基于 fieldId，计划废弃，请迁移至 /api/data/table/detail")
    public ApiResponse<DataBlock> getDetailByReq(@RequestBody DetailQueryReq req) {
        try {
            DataBlock result = dataApi.getDetail(req);
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

    @Deprecated
    @GetMapping("/{moduleId}/{id}")
    @Operation(
            summary = "通用单条详情查询 (GET 快捷路径) (将废弃)",
            description = "旧版基于 fieldId，计划废弃，推荐使用 /{moduleId}/{id}/full 或 /api/data/table/detail")
    public ApiResponse<DataBlock> getDetail(
            @PathVariable Long moduleId,
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> fields) {
        try {
            DataBlock result = dataApi.getDetail(moduleId, id, fields);
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

    @PostMapping("/table/query")
    @Operation(summary = "物理表驱动动态分页查询", description = "支持直接使用物理表名、列名规格及多条件过滤进行跨自相似树查询 (脱离 fieldId)")
    public ApiResponse<DataPage<DataBlock>> queryByTable(
            @RequestBody com.jdec.platform.data.api.dto.request.TablePageQueryReq req) {
        try {
            DataPage<DataBlock> result = dataApi.queryByTable(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine queryByTable failed: moduleId={}, err={}",
                    req.getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/table/detail")
    @Operation(summary = "物理表驱动单条详情查询", description = "支持直接根据物理表名及列名规约加载单条业务对象详情，返回读写同构的 DataBlock")
    public ApiResponse<DataBlock> getDetailByTable(
            @RequestBody com.jdec.platform.data.api.dto.request.TableDetailQueryReq req) {
        try {
            DataBlock result = dataApi.getDetailByTable(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getDetailByTable failed: moduleId={}, id={}, err={}",
                    req.getModuleId(),
                    req.getId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/table/count")
    @Operation(summary = "物理表驱动动态计数查询", description = "返回满足基于物理表/列过滤条件的记录总数 (脱离 fieldId)")
    public ApiResponse<Long> countByTable(
            @RequestBody com.jdec.platform.data.api.dto.request.TableQueryReq req) {
        try {
            Long result = dataApi.countByTable(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine countByTable failed: moduleId={}, err={}",
                    req.getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/table/options")
    @Operation(summary = "物理表驱动字段搜索下拉候选项", description = "支持直接根据物理表名及列名获取去重候选值列表 (脱离 fieldId)")
    public ApiResponse<List<DataOption>> getOptionsByTable(
            @RequestBody com.jdec.platform.data.api.dto.request.TableOptionReq req) {
        try {
            List<DataOption> result = dataApi.getOptionsByTable(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getOptionsByTable failed: moduleId={}, table={}, col={}, err={}",
                    req.getModuleId(),
                    req.getTableName(),
                    req.getColumnName(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/{moduleId}/{id}/full")
    @Operation(
            summary = "通用单条全量子孙树详情查询 (GET 快捷全量物理树)",
            description = "自动递归抓取当前模块及所有伴生表、全部子孙物理表的全部列信息，返回完整 DataBlock")
    public ApiResponse<DataBlock> getFullDetail(
            @Parameter(description = "主模块 ID", example = "1001") @PathVariable Long moduleId,
            @Parameter(description = "单据主键 ID", example = "100") @PathVariable Long id) {
        try {
            DataBlock result = dataApi.getFullDetail(moduleId, id);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getFullDetail failed: moduleId={}, id={}, err={}",
                    moduleId,
                    id,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(
            summary = "通用同构原子保存",
            description = "入参结构与 getDetail 响应的 DataBlock 100% 读写同构，直接提交 DataBlock，返回 SaveResp")
    public ApiResponse<SaveResp> save(@RequestBody DataBlock block) {
        try {
            SaveResp resp = dataApi.save(block);
            return ApiResponse.success(resp);
        } catch (Exception ex) {
            log.error(
                    "Data engine save failed: moduleId={}, err={}",
                    block != null ? block.getModuleId() : null,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/table/fork-version")
    @Operation(
            summary = "单据版本派生与局部增量融合保存",
            description = "基于基准单据ID(如156)自动抓取完整数据树，并与前端提交的局部增量修改(patchData)深度合并，生成全新的独立单据版本并返回新根ID")
    public ApiResponse<Long> forkNewVersion(
            @RequestBody com.jdec.platform.data.api.dto.request.ForkVersionReq req) {
        try {
            Long newId = dataApi.forkNewVersion(req);
            return ApiResponse.success(newId);
        } catch (Exception ex) {
            log.error(
                    "Data engine forkNewVersion failed: moduleId={}, baseId={}, err={}",
                    req != null ? req.getModuleId() : null,
                    req != null ? req.getBaseId() : null,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
