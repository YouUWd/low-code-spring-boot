package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.request.CreatePermissionReq;
import com.jdec.platform.config.api.dto.request.UpdatePermissionReq;
import com.jdec.platform.config.api.dto.response.ModulePermissionResp;
import com.jdec.platform.config.biz.converter.PermissionConverter;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.service.SysModulePermissionService;
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
 * 权限管理 REST 控制器
 *
 * <p>提供权限的增删改查接口。
 */
@RestController
@RequestMapping("/api/config/permissions")
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限的增删改查接口")
public class SysModulePermissionController {

    private final SysModulePermissionService permissionService;
    private final PermissionConverter permissionConverter;

    /**
     * 获取所有权限
     *
     * @return 权限列表
     */
    @GetMapping
    @Operation(summary = "获取所有权限", description = "获取系统中所有的权限列表")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
    })
    public ApiResponse<List<ModulePermissionResp>> listPermissions() {
        List<SysModulePermission> permissions = permissionService.getAllPermissions();
        return ApiResponse.success(permissionConverter.toModulePermissionResps(permissions));
    }

    /**
     * 根据权限 ID 获取权限详情
     *
     * @param permissionId 权限 ID
     * @return 权限详情
     */
    @GetMapping("/{permissionId}")
    @Operation(summary = "获取权限详情", description = "根据权限 ID 获取权限的详细信息")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "权限不存在"),
    })
    public ApiResponse<ModulePermissionResp> getPermission(
            @Parameter(description = "权限 ID", example = "1")
            @PathVariable Long permissionId) {
        SysModulePermission permission = permissionService.getPermissionById(permissionId);
        return Optional.ofNullable(permission)
                .map(p -> ApiResponse.success(permissionConverter.toModulePermissionResp(p)))
                .orElseGet(() -> ApiResponse.error(404, "权限不存在"));
    }

    /**
     * 创建新权限
     *
     * @param request 权限创建请求
     * @return 创建的权限信息
     */
    @PostMapping
    @Operation(summary = "创建权限", description = "创建一个新的权限")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<ModulePermissionResp> createPermission(@RequestBody CreatePermissionReq request) {
        SysModulePermission permission = SysModulePermission.builder()
                .moduleId(request.getModuleId())
                .fieldCode(request.getFieldCode())
                .permissionType(request.getPermissionType())
                .permissionCode(request.getPermissionCode())
                .permissionName(request.getPermissionName())
                .description(request.getDescription())
                .build();
        SysModulePermission created = permissionService.createPermission(permission);
        return ApiResponse.success("权限创建成功", permissionConverter.toModulePermissionResp(created));
    }

    /**
     * 更新权限信息
     *
     * @param permissionId 权限 ID
     * @param request 权限更新请求
     * @return 更新后的权限信息
     */
    @PutMapping("/{permissionId}")
    @Operation(summary = "更新权限", description = "更新指定权限的信息")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "权限不存在"),
    })
    public ApiResponse<ModulePermissionResp> updatePermission(
            @Parameter(description = "权限 ID", example = "1")
            @PathVariable Long permissionId,
            @RequestBody UpdatePermissionReq request) {
        SysModulePermission permission = SysModulePermission.builder()
                .id(permissionId)
                .permissionType(request.getPermissionType())
                .permissionCode(request.getPermissionCode())
                .permissionName(request.getPermissionName())
                .description(request.getDescription())
                .build();
        SysModulePermission updated = permissionService.updatePermission(permission);
        return ApiResponse.success("权限更新成功", permissionConverter.toModulePermissionResp(updated));
    }

    /**
     * 删除权限
     *
     * @param permissionId 权限 ID
     * @return 删除结果
     */
    @DeleteMapping("/{permissionId}")
    @Operation(summary = "删除权限", description = "删除指定的权限")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "权限不存在"),
    })
    public ApiResponse<Void> deletePermission(
            @Parameter(description = "权限 ID", example = "1")
            @PathVariable Long permissionId) {
        permissionService.deletePermission(permissionId);
        return ApiResponse.success();
    }
}
