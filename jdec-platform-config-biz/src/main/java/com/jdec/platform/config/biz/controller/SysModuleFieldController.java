package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleFieldApi;
import com.jdec.platform.config.api.dto.request.CreateSysModuleFieldReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleFieldReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleFieldReq;
import com.jdec.platform.config.api.dto.response.SysModuleFieldDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleFieldEntryResp;
import com.jdec.platform.shared.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模块字段管理 Controller
 */
@RestController
@RequestMapping("/api/config/module-fields")
@RequiredArgsConstructor
public class SysModuleFieldController {

    private final SysModuleFieldApi sysModuleFieldService;

    /**
     * 创建模块字段
     */
    @PostMapping
    public ApiResponse<SysModuleFieldEntryResp> createModuleField(@RequestBody CreateSysModuleFieldReq request) {
        SysModuleFieldEntryResp result = sysModuleFieldService.createModuleField(request);
        return ApiResponse.success("创建成功", result);
    }

    /**
     * 获取模块字段详情
     */
    @GetMapping("/{fieldId}")
    public ApiResponse<SysModuleFieldDetailResp> getModuleField(@PathVariable Long fieldId) {
        SysModuleFieldDetailResp result = sysModuleFieldService.getModuleFieldById(fieldId);
        return ApiResponse.success(result);
    }

    /**
     * 获取模块下的所有字段
     */
    @GetMapping("/module/{moduleId}")
    public ApiResponse<List<SysModuleFieldEntryResp>> getFieldsByModule(@PathVariable Long moduleId) {
        List<SysModuleFieldEntryResp> result = sysModuleFieldService.getFieldsByModule(moduleId);
        return ApiResponse.success(result);
    }

    /**
     * 查询模块字段
     */
    @PostMapping("/query")
    public ApiResponse<List<SysModuleFieldEntryResp>> queryModuleFields(@RequestBody QuerySysModuleFieldReq request) {
        List<SysModuleFieldEntryResp> result = sysModuleFieldService.queryModuleFields(request);
        return ApiResponse.success(result);
    }

    /**
     * 更新模块字段
     */
    @PutMapping("/{fieldId}")
    public ApiResponse<SysModuleFieldEntryResp> updateModuleField(@RequestBody UpdateSysModuleFieldReq request) {
        SysModuleFieldEntryResp result = sysModuleFieldService.updateModuleField(request);
        return ApiResponse.success("更新成功", result);
    }

    /**
     * 删除模块字段
     */
    @DeleteMapping("/{fieldId}")
    public ApiResponse<Void> deleteModuleField(@PathVariable Long fieldId) {
        sysModuleFieldService.deleteModuleField(fieldId);
        return ApiResponse.success("删除成功");
    }

    /**
     * 批量删除模块字段
     */
    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDeleteModuleFields(@RequestBody List<Long> fieldIds) {
        sysModuleFieldService.batchDeleteModuleFields(fieldIds);
        return ApiResponse.success("批量删除成功");
    }
}
