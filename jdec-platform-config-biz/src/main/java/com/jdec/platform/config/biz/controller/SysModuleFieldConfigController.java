package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleFieldConfigApi;
import com.jdec.platform.config.api.dto.request.ModuleFieldConfigReq;
import com.jdec.platform.config.api.dto.response.ModuleFieldConfigResp;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模块字段配置 REST 控制器
 *
 * <p>提供模块字段配置的管理接口。
 */
@RestController
@RequestMapping("/api/config/module-field-configs")
@RequiredArgsConstructor
@Tag(name = "模块字段配置", description = "模块字段配置的管理接口")
public class SysModuleFieldConfigController {

    private final SysModuleFieldConfigApi fieldConfigService;

    /**
     * 获取模块的字段配置列表
     *
     * @param moduleCode 模块编码
     * @return 字段配置列表
     */
    @GetMapping("/{moduleCode}")
    @Operation(summary = "获取模块字段配置列表", description = "获取指定模块的所有字段配置")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
    })
    public ApiResponse<List<ModuleFieldConfigResp>> listFieldConfigs(
            @Parameter(description = "模块编码", example = "user_base")
            @PathVariable String moduleCode) {
        List<ModuleFieldConfigResp> configs =
                fieldConfigService.getModuleFieldConfigs(moduleCode);
        return ApiResponse.success(configs);
    }

    /**
     * 添加字段配置
     *
     * @param moduleCode 模块编码
     * @param request 字段配置请求
     * @return 添加的字段配置
     */
    @PostMapping("/{moduleCode}")
    @Operation(summary = "添加字段配置", description = "为指定模块添加字段配置")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "添加成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<ModuleFieldConfigResp> addFieldConfig(
            @Parameter(description = "模块编码", example = "user_base")
            @PathVariable String moduleCode,
            @RequestBody ModuleFieldConfigReq request) {
        ModuleFieldConfigResp result = fieldConfigService.addFieldConfig(moduleCode, request);
        return ApiResponse.success("字段配置添加成功", result);
    }

    /**
     * 更新单个字段配置
     *
     * @param moduleCode 模块编码
     * @param fieldCode 字段编码
     * @param request 字段配置请求
     * @return 更新后的字段配置
     */
    @PutMapping("/{moduleCode}/{fieldCode}")
    @Operation(summary = "更新字段配置", description = "更新指定模块的字段配置")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<ModuleFieldConfigResp> updateFieldConfig(
            @Parameter(description = "模块编码", example = "user_base")
            @PathVariable String moduleCode,
            @Parameter(description = "字段编码", example = "name")
            @PathVariable String fieldCode,
            @RequestBody ModuleFieldConfigReq request) {
        ModuleFieldConfigResp result =
                fieldConfigService.updateFieldConfig(moduleCode, fieldCode, request);
        return ApiResponse.success("字段配置更新成功", result);
    }

    /**
     * 删除字段配置
     *
     * @param moduleCode 模块编码
     * @param fieldCode 字段编码
     * @return 删除结果
     */
    @DeleteMapping("/{moduleCode}/{fieldCode}")
    @Operation(summary = "删除字段配置", description = "删除指定模块的字段配置")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
    })
    public ApiResponse<Void> deleteFieldConfig(
            @Parameter(description = "模块编码", example = "user_base")
            @PathVariable String moduleCode,
            @Parameter(description = "字段编码", example = "name")
            @PathVariable String fieldCode) {
        fieldConfigService.deleteFieldConfig(moduleCode, fieldCode);
        return ApiResponse.success();
    }
}
