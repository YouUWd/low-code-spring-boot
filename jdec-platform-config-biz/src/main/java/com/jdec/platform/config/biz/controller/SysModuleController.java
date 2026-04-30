package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.CreateSysModuleReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SortOrderUpdateReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleEntryResp;
import com.jdec.platform.config.api.dto.response.SysModuleTreeResp;

import com.jdec.platform.shared.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模块管理 Controller
 */
@RestController
@RequestMapping("/api/config/modules")
@RequiredArgsConstructor
public class SysModuleController {

    private final SysModuleApi sysModuleService;

    /**
     * 创建模块
     */
    @PostMapping
    public ApiResponse<SysModuleEntryResp> createModule(@RequestBody CreateSysModuleReq request) {
        SysModuleEntryResp result = sysModuleService.createModule(request);
        return ApiResponse.success("创建成功", result);
    }

    /**
     * 获取模块详情
     */
    @GetMapping("/{moduleId}")
    public ApiResponse<SysModuleDetailResp> getModule(@PathVariable Long moduleId) {
        SysModuleDetailResp result = sysModuleService.getModuleById(moduleId);
        return ApiResponse.success(result);
    }

    /**
     * 获取模块树
     */
    @GetMapping("/tree/{tenantId}")
    public ApiResponse<List<SysModuleTreeResp>> getModuleTree(@PathVariable Long tenantId) {
        List<SysModuleTreeResp> result = sysModuleService.getModuleTree(tenantId);
        return ApiResponse.success(result);
    }

    /**
     * 查询模块列表
     */
    @PostMapping("/query")
    public ApiResponse<List<SysModuleEntryResp>> queryModules(@RequestBody QuerySysModuleReq request) {
        List<SysModuleEntryResp> result = sysModuleService.queryModules(request);
        return ApiResponse.success(result);
    }

    /**
     * 更新模块
     */
    @PutMapping("/{moduleId}")
    public ApiResponse<SysModuleEntryResp> updateModule(@RequestBody UpdateSysModuleReq request) {
        SysModuleEntryResp result = sysModuleService.updateModule(request);
        return ApiResponse.success("更新成功", result);
    }

    /**
     * 删除模块
     */
    @DeleteMapping("/{moduleId}")
    public ApiResponse<Void> deleteModule(@PathVariable Long moduleId) {
        sysModuleService.deleteModule(moduleId);
        return ApiResponse.success("删除成功");
    }

    /**
     * 批量删除模块
     */
    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDeleteModules(@RequestBody List<Long> moduleIds) {
        sysModuleService.batchDeleteModules(moduleIds);
        return ApiResponse.success("批量删除成功");
    }

    /**
     * 批量更新模块排序
     */
    @PostMapping("/batch-update-sort-order")
    public ApiResponse<Void> batchUpdateSortOrder(@RequestBody List<SortOrderUpdateReq> requests) {
        sysModuleService.batchUpdateSortOrder(requests);
        return ApiResponse.success("排序更新成功");
    }

    /**
     * 保存模块完整信息
     * 包含模块基本信息、关联表、字段配置和状态
     */
    @PostMapping("/save")
    public ApiResponse<SysModuleDetailResp> saveModule(@RequestBody SaveModuleReq request) {
        SysModuleDetailResp result = sysModuleService.saveModule(request);
        return ApiResponse.success("保存成功", result);
    }
}
