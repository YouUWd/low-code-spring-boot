package com.jdec.platform.common_business.biz.controller;

import com.jdec.platform.common_business.api.PersonalizedUserServiceApi;
import com.jdec.platform.common_business.api.dto.request.PersonalizedModuleReq;
import com.jdec.platform.common_business.api.dto.response.PersonalizedModuleResp;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/student/setting")
@Tag(
        name = "通用业务层个性化配置", // 分组名称（文档中显示的分组标签）
        description = "通用业务层个性化配置" // 分组描述
        )
public class PersonalizedSettingController {

    private final PersonalizedUserServiceApi personalizedUserService;

    @PostMapping("/personalized/save")
    @Operation(summary = "保存用户模块个性化配置", description = "保存用户模块个性化配置")
    public ApiResponse<Boolean> saveUserPersonalizedSetting(
            @RequestBody List<PersonalizedModuleReq> reqs) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                personalizedUserService.saveUserPersonalizedModuleSetting(
                        LoginUserUtil.getRealLoginUserId(),
                        LoginUserUtil.getProjectNoFromHeader(),
                        reqs));
    }

    @PostMapping("/personalized/restore")
    @Operation(summary = "恢复用户模块默认个性化配置", description = "恢复用户模块默认个性化配置")
    public ApiResponse<Boolean> restoreUserPersonalizedSetting(@RequestParam Long moduleId) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                personalizedUserService.restoreToDefaultSetting(
                        LoginUserUtil.getRealLoginUserId(),
                        LoginUserUtil.getRoleId(),
                        LoginUserUtil.getSubjectIdFromHeader(),
                        LoginUserUtil.getProjectNoFromHeader(),
                        moduleId));
    }

    @GetMapping("/personalized/get")
    @Operation(summary = "批量获取用户模块个性化配置", description = "批量获取用户模块个性化配置")
    public ApiResponse<List<PersonalizedModuleResp>> getUserPersonalizedSetting() {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                personalizedUserService.getUserPersonalizedModuleSetting(
                        LoginUserUtil.getRealLoginUserId(),
                        LoginUserUtil.getLoginSubjectId(),
                        LoginUserUtil.getRoleId(),
                        LoginUserUtil.getProjectNoFromHeader()));
    }

    @GetMapping("/personalized/get-by-module")
    @Operation(summary = "获取用户单个模块个性化配置", description = "获取用户单个模块个性化配置")
    public ApiResponse<PersonalizedModuleResp> getUserPersonalizedSettingByModuleId(
            @RequestParam Long moduleId) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                personalizedUserService.getUserPersonalizedModuleSettingByModuleId(
                        LoginUserUtil.getRealLoginUserId(),
                        LoginUserUtil.getLoginSubjectId(),
                        LoginUserUtil.getRoleId(),
                        LoginUserUtil.getProjectNoFromHeader(),
                        moduleId));
    }
}
