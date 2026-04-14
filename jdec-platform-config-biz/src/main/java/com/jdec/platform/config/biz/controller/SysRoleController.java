package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.request.CreateRoleReq;
import com.jdec.platform.config.api.dto.request.UpdateRoleReq;
import com.jdec.platform.config.api.dto.response.RoleEntryResp;
import com.jdec.platform.config.biz.converter.PermissionConverter;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.service.SysRoleService;
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
 * 角色管理 REST 控制器
 *
 * <p>提供角色的增删改查接口。
 */
@RestController
@RequestMapping("/api/config/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色的增删改查接口")
public class SysRoleController {

    private final SysRoleService roleService;
    private final PermissionConverter permissionConverter;

    /**
     * 获取所有角色
     *
     * @return 角色列表
     */
    @GetMapping
    @Operation(summary = "获取所有角色", description = "获取系统中所有的角色列表")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
    })
    public ApiResponse<List<RoleEntryResp>> listRoles() {
        List<SysRole> roles = roleService.getAllRoles();
        return ApiResponse.success(permissionConverter.toRoleEntries(roles));
    }

    /**
     * 根据角色 ID 获取角色详情
     *
     * @param roleId 角色 ID
     * @return 角色详情
     */
    @GetMapping("/{roleId}")
    @Operation(summary = "获取角色详情", description = "根据角色 ID 获取角色的详细信息")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "角色不存在"),
    })
    public ApiResponse<RoleEntryResp> getRole(
            @Parameter(description = "角色 ID", example = "1")
            @PathVariable Long roleId) {
        SysRole role = roleService.getRoleById(roleId);
        return Optional.ofNullable(role)
                .map(r -> ApiResponse.success(permissionConverter.toRoleEntry(r)))
                .orElseGet(() -> ApiResponse.error(404, "角色不存在"));
    }

    /**
     * 创建新角色
     *
     * @param request 角色创建请求
     * @return 创建的角色信息
     */
    @PostMapping
    @Operation(summary = "创建角色", description = "创建一个新的角色")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<RoleEntryResp> createRole(@RequestBody CreateRoleReq request) {
        SysRole role = SysRole.builder()
                .roleCode(request.getRoleCode())
                .roleName(request.getRoleName())
                .description(request.getDescription())
                .build();
        SysRole created = roleService.createRole(role);
        return ApiResponse.success("角色创建成功", permissionConverter.toRoleEntry(created));
    }

    /**
     * 更新角色信息
     *
     * @param roleId 角色 ID
     * @param request 角色更新请求
     * @return 更新后的角色信息
     */
    @PutMapping("/{roleId}")
    @Operation(summary = "更新角色", description = "更新指定角色的信息")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "角色不存在"),
    })
    public ApiResponse<RoleEntryResp> updateRole(
            @Parameter(description = "角色 ID", example = "1")
            @PathVariable Long roleId,
            @RequestBody UpdateRoleReq request) {
        SysRole role = SysRole.builder()
                .id(roleId)
                .roleName(request.getRoleName())
                .description(request.getDescription())
                .build();
        SysRole updated = roleService.updateRole(role);
        return ApiResponse.success("角色更新成功", permissionConverter.toRoleEntry(updated));
    }

    /**
     * 删除角色
     *
     * @param roleId 角色 ID
     * @return 删除结果
     */
    @DeleteMapping("/{roleId}")
    @Operation(summary = "删除角色", description = "删除指定的角色")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "角色不存在"),
    })
    public ApiResponse<Void> deleteRole(
            @Parameter(description = "角色 ID", example = "1")
            @PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return ApiResponse.success();
    }
}
