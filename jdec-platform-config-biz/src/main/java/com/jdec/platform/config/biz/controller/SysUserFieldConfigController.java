package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysUserFieldConfigApi;
import com.jdec.platform.config.api.dto.request.UserFieldConfigReq;
import com.jdec.platform.config.api.dto.response.UserFieldConfigResp;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 用户字段配置 REST 控制器 */
@RestController
@RequestMapping("/api/config/user-field-config")
@RequiredArgsConstructor
public class SysUserFieldConfigController {

    private final SysUserFieldConfigApi userFieldConfigService;

    /** 获取用户自定义的字段配置 GET /api/config/user-field-config/{userId}/{moduleCode} */
    @GetMapping("/{userId}/{moduleCode}")
    public ApiResponse<List<UserFieldConfigResp>> getUserFieldConfigs(
            @PathVariable Long userId, @PathVariable String moduleCode) {
        try {
            List<UserFieldConfigResp> configs =
                    userFieldConfigService.getUserFieldConfigs(userId, moduleCode);
            return ApiResponse.success(configs);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "获取用户字段配置失败");
        }
    }

    /** 保存用户自定义的字段配置 POST /api/config/user-field-config/{userId}/{moduleCode} */
    @PostMapping("/{userId}/{moduleCode}")
    public ApiResponse<Void> saveUserFieldConfigs(
            @PathVariable Long userId,
            @PathVariable String moduleCode,
            @RequestBody List<UserFieldConfigReq> requests) {
        try {
            userFieldConfigService.saveUserFieldConfigs(userId, moduleCode, requests);
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "用户字段配置保存失败");
        }
    }

    /** 重置用户字段配置为默认 DELETE /api/config/user-field-config/{userId}/{moduleCode} */
    @DeleteMapping("/{userId}/{moduleCode}")
    public ApiResponse<Void> resetUserFieldConfigs(
            @PathVariable Long userId, @PathVariable String moduleCode) {
        try {
            userFieldConfigService.resetUserFieldConfigs(userId, moduleCode);
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "用户字段配置重置失败");
        }
    }
}
