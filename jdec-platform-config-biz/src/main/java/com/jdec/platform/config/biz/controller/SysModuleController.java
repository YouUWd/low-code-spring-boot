package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.RegisterModuleReq;
import com.jdec.platform.config.api.dto.request.UpdateModuleReq;
import com.jdec.platform.config.api.dto.response.ModuleEntryResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
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
 * 模块管理 REST 控制器
 *
 * <p>提供模块的增删改查接口。
 */
@RestController
@RequestMapping("/api/config/modules")
@RequiredArgsConstructor
@Tag(name = "模块管理", description = "模块的增删改查接口")
public class SysModuleController {

    private final SysModuleApi moduleService;

    /**
     * 获取所有模块
     *
     * @return 模块列表
     */
    @GetMapping
    @Operation(summary = "获取所有模块", description = "获取系统中所有的模块列表")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
    })
    public ApiResponse<List<ModuleEntryResp>> listModules() {
        List<ModuleEntryResp> modules = moduleService.getModuleList();
        return ApiResponse.success(modules);
    }

    /**
     * 根据模块 ID 获取模块详情
     *
     * @param moduleId 模块 ID
     * @return 模块详情
     */
    @GetMapping("/{moduleId}")
    @Operation(summary = "获取模块详情", description = "根据模块 ID 获取模块的详细信息")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "模块不存在"),
    })
    public ApiResponse<ModuleEntryResp> getModule(
            @Parameter(description = "模块 ID", example = "1")
            @PathVariable Long moduleId) {
        ModuleEntryResp module = moduleService.getModuleById(moduleId);
        return Optional.ofNullable(module)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "模块不存在"));
    }

    /**
     * 创建新模块
     *
     * @param request 模块创建请求
     * @return 创建的模块信息
     */
    @PostMapping
    @Operation(summary = "创建模块", description = "创建一个新的模块")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<ModuleEntryResp> createModule(@RequestBody RegisterModuleReq request) {
        ModuleEntryResp module = moduleService.registerModule(request);
        return ApiResponse.success("模块创建成功", module);
    }

    /**
     * 更新模块信息
     *
     * @param moduleId 模块 ID
     * @param request 模块更新请求
     * @return 更新后的模块信息
     */
    @PutMapping("/{moduleId}")
    @Operation(summary = "更新模块", description = "更新指定模块的信息")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "模块不存在"),
    })
    public ApiResponse<ModuleEntryResp> updateModule(
            @Parameter(description = "模块 ID", example = "1")
            @PathVariable Long moduleId,
            @RequestBody UpdateModuleReq request) {
        ModuleEntryResp module = moduleService.updateModule(moduleId, request);
        return ApiResponse.success("模块更新成功", module);
    }

    /**
     * 删除模块
     *
     * @param moduleId 模块 ID
     * @return 删除结果
     */
    @DeleteMapping("/{moduleId}")
    @Operation(summary = "删除模块", description = "删除指定的模块")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "模块不存在"),
    })
    public ApiResponse<Void> deleteModule(
            @Parameter(description = "模块 ID", example = "1")
            @PathVariable Long moduleId) {
        moduleService.deleteModule(moduleId);
        return ApiResponse.success();
    }
}
