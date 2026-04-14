package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysUserFieldConfigApi;
import com.jdec.platform.config.api.dto.request.UserFieldConfigReq;
import com.jdec.platform.config.api.dto.response.UserFieldConfigResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户字段配置 REST 控制器
 *
 * <p>提供用户自定义字段配置的管理接口。
 */
@RestController
@RequestMapping("/api/config/user-field-configs")
@RequiredArgsConstructor
@Tag(name = "用户字段配置", description = "用户自定义字段配置的管理接口")
public class SysUserFieldConfigController {

    private final SysUserFieldConfigApi userFieldConfigService;

    /**
     * 获取用户自定义的字段配置
     *
     * @param userId 用户 ID
     * @param moduleCode 模块编码
     * @return 字段配置列表
     */
    @GetMapping("/{userId}/{moduleCode}")
    @Operation(summary = "获取用户字段配置", description = "获取指定用户在指定模块的字段配置")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
    })
    public ApiResponse<List<UserFieldConfigResp>> getUserFieldConfigs(
            @Parameter(description = "用户 ID", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "模块编码", example = "user_base")
            @PathVariable String moduleCode) {
        List<UserFieldConfigResp> configs =
                userFieldConfigService.getUserFieldConfigs(userId, moduleCode);
        return ApiResponse.success(configs);
    }

    /**
     * 保存用户自定义的字段配置
     *
     * @param userId 用户 ID
     * @param moduleCode 模块编码
     * @param requests 字段配置请求列表
     * @return 保存结果
     */
    @PostMapping("/{userId}/{moduleCode}")
    @Operation(summary = "保存用户字段配置", description = "保存用户在指定模块的字段配置")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "保存成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<Void> saveUserFieldConfigs(
            @Parameter(description = "用户 ID", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "模块编码", example = "user_base")
            @PathVariable String moduleCode,
            @RequestBody List<UserFieldConfigReq> requests) {
        userFieldConfigService.saveUserFieldConfigs(userId, moduleCode, requests);
        return ApiResponse.success();
    }

    /**
     * 重置用户字段配置为默认
     *
     * @param userId 用户 ID
     * @param moduleCode 模块编码
     * @return 重置结果
     */
    @DeleteMapping("/{userId}/{moduleCode}")
    @Operation(summary = "重置用户字段配置", description = "将用户在指定模块的字段配置重置为默认")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "重置成功"),
    })
    public ApiResponse<Void> resetUserFieldConfigs(
            @Parameter(description = "用户 ID", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "模块编码", example = "user_base")
            @PathVariable String moduleCode) {
        userFieldConfigService.resetUserFieldConfigs(userId, moduleCode);
        return ApiResponse.success();
    }
}
