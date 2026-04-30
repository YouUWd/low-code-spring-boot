package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleTableApi;
import com.jdec.platform.config.api.dto.request.CreateSysModuleTableReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleTableReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleTableReq;
import com.jdec.platform.config.api.dto.response.SysModuleTableDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleTableEntryResp;
import com.jdec.platform.shared.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模块表管理 Controller
 */
@RestController
@RequestMapping("/api/config/module-tables")
@RequiredArgsConstructor
public class SysModuleTableController {

    private final SysModuleTableApi sysModuleTableService;

    /**
     * 创建模块表
     */
    @PostMapping
    public ApiResponse<SysModuleTableEntryResp> createModuleTable(@RequestBody CreateSysModuleTableReq request) {
        SysModuleTableEntryResp result = sysModuleTableService.createModuleTable(request);
        return ApiResponse.success("创建成功", result);
    }

    /**
     * 获取模块表详情
     */
    @GetMapping("/{tableId}")
    public ApiResponse<SysModuleTableDetailResp> getModuleTable(@PathVariable Long tableId) {
        SysModuleTableDetailResp result = sysModuleTableService.getModuleTableById(tableId);
        return ApiResponse.success(result);
    }

    /**
     * 获取模块下的所有表
     */
    @GetMapping("/module/{moduleId}")
    public ApiResponse<List<SysModuleTableEntryResp>> getTablesByModule(@PathVariable Long moduleId) {
        List<SysModuleTableEntryResp> result = sysModuleTableService.getTablesByModule(moduleId);
        return ApiResponse.success(result);
    }

    /**
     * 查询模块表
     */
    @PostMapping("/query")
    public ApiResponse<List<SysModuleTableEntryResp>> queryModuleTables(@RequestBody QuerySysModuleTableReq request) {
        List<SysModuleTableEntryResp> result = sysModuleTableService.queryModuleTables(request);
        return ApiResponse.success(result);
    }

    /**
     * 更新模块表
     */
    @PutMapping("/{tableId}")
    public ApiResponse<SysModuleTableEntryResp> updateModuleTable(@RequestBody UpdateSysModuleTableReq request) {
        SysModuleTableEntryResp result = sysModuleTableService.updateModuleTable(request);
        return ApiResponse.success("更新成功", result);
    }

    /**
     * 删除模块表
     */
    @DeleteMapping("/{tableId}")
    public ApiResponse<Void> deleteModuleTable(@PathVariable Long tableId) {
        sysModuleTableService.deleteModuleTable(tableId);
        return ApiResponse.success("删除成功");
    }

    /**
     * 批量删除模块表
     */
    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDeleteModuleTables(@RequestBody List<Long> tableIds) {
        sysModuleTableService.batchDeleteModuleTables(tableIds);
        return ApiResponse.success("批量删除成功");
    }
}
