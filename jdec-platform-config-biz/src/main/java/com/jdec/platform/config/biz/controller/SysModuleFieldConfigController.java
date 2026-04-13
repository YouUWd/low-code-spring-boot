package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleFieldConfigApi;
import com.jdec.platform.config.api.dto.request.ModuleFieldConfigReq;
import com.jdec.platform.config.api.dto.response.ModuleFieldConfigResp;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 模块字段配置 REST 控制器 */
@RestController
@RequestMapping("/api/config/module-field-config")
@RequiredArgsConstructor
public class SysModuleFieldConfigController {

    private final SysModuleFieldConfigApi fieldConfigService;

    /** 获取模块的字段配置 GET /api/config/module-field-config/{moduleCode} */
    @GetMapping("/{moduleCode}")
    public ApiResponse<List<ModuleFieldConfigResp>> getModuleFieldConfigs(
            @PathVariable String moduleCode) {
        try {
            List<ModuleFieldConfigResp> configs =
                    fieldConfigService.getModuleFieldConfigs(moduleCode);
            return ApiResponse.success(configs);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "获取字段配置失败");
        }
    }

    /** 添加字段配置 POST /api/config/module-field-config/{moduleCode} */
    @PostMapping("/{moduleCode}")
    public ApiResponse<ModuleFieldConfigResp> addFieldConfig(
            @PathVariable String moduleCode, @RequestBody ModuleFieldConfigReq request) {
        try {
            ModuleFieldConfigResp result = fieldConfigService.addFieldConfig(moduleCode, request);
            return ApiResponse.success("字段配置添加成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "字段配置添加失败");
        }
    }

    /** 更新字段配置 PUT /api/config/module-field-config/{moduleCode}/{fieldCode} */
    @PutMapping("/{moduleCode}/{fieldCode}")
    public ApiResponse<ModuleFieldConfigResp> updateFieldConfig(
            @PathVariable String moduleCode,
            @PathVariable String fieldCode,
            @RequestBody ModuleFieldConfigReq request) {
        try {
            ModuleFieldConfigResp result =
                    fieldConfigService.updateFieldConfig(moduleCode, fieldCode, request);
            return ApiResponse.success("字段配置更新成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "字段配置更新失败");
        }
    }

    /** 批量更新字段配置 PUT /api/config/module-field-config/{moduleCode} */
    @PutMapping("/{moduleCode}")
    public ApiResponse<List<ModuleFieldConfigResp>> updateFieldConfigs(
            @PathVariable String moduleCode, @RequestBody List<ModuleFieldConfigReq> requests) {
        try {
            List<ModuleFieldConfigResp> result =
                    fieldConfigService.updateFieldConfigs(moduleCode, requests);
            return ApiResponse.success("字段配置批量更新成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "字段配置批量更新失败");
        }
    }

    /** 删除字段配置 DELETE /api/config/module-field-config/{moduleCode}/{fieldCode} */
    @DeleteMapping("/{moduleCode}/{fieldCode}")
    public ApiResponse<Void> deleteFieldConfig(
            @PathVariable String moduleCode, @PathVariable String fieldCode) {
        try {
            fieldConfigService.deleteFieldConfig(moduleCode, fieldCode);
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "字段配置删除失败");
        }
    }
}
