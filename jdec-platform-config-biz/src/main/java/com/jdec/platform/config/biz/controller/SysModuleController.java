package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.RegisterModuleReq;
import com.jdec.platform.config.api.dto.request.UpdateModuleReq;
import com.jdec.platform.config.api.dto.response.ModuleEntryResp;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 模块管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/module")
@RequiredArgsConstructor
public class SysModuleController {

    private final SysModuleApi moduleService;

    // ==================== 模块管理接口 ====================

    /** 获取所有模块 GET /api/config/module */
    @GetMapping
    public ApiResponse<List<ModuleEntryResp>> getModuleList() {
        List<ModuleEntryResp> modules = moduleService.getModuleList();
        return ApiResponse.success(modules);
    }

    /** 根据 ID 获取模块 GET /api/config/module/{moduleId} */
    @GetMapping("/{moduleId}")
    public ApiResponse<ModuleEntryResp> getModuleById(@PathVariable Long moduleId) {
        ModuleEntryResp module = moduleService.getModuleById(moduleId);
        if (module == null) {
            return ApiResponse.error(404, "模块不存在");
        }
        return ApiResponse.success(module);
    }

    /** 根据编码获取模块 GET /api/config/module/code/{moduleCode} */
    @GetMapping("/code/{moduleCode}")
    public ApiResponse<ModuleEntryResp> getModuleByCode(@PathVariable String moduleCode) {
        ModuleEntryResp module = moduleService.getModuleByCode(moduleCode);
        if (module == null) {
            return ApiResponse.error(404, "模块不存在");
        }
        return ApiResponse.success(module);
    }

    /** 注册新模块 POST /api/config/module */
    @PostMapping
    public ApiResponse<ModuleEntryResp> registerModule(@RequestBody RegisterModuleReq request) {
        try {
            ModuleEntryResp module = moduleService.registerModule(request);
            return ApiResponse.success("模块创建成功", module);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "模块创建失败");
        }
    }

    /** 更新模块 PUT /api/config/module/{moduleId} */
    @PutMapping("/{moduleId}")
    public ApiResponse<ModuleEntryResp> updateModule(
            @PathVariable Long moduleId, @RequestBody UpdateModuleReq request) {
        try {
            ModuleEntryResp module = moduleService.updateModule(moduleId, request);
            return ApiResponse.success("模块更新成功", module);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "模块更新失败");
        }
    }

    /** 删除模块 DELETE /api/config/module/{moduleId} */
    @DeleteMapping("/{moduleId}")
    public ApiResponse<Void> deleteModule(@PathVariable Long moduleId) {
        try {
            moduleService.deleteModule(moduleId);
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "模块删除失败");
        }
    }
}
