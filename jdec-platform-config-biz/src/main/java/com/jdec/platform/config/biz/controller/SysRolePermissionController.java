package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.request.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/sys/role")
@RequiredArgsConstructor
@Tag(name = "角色权限管理", description = "角色权限分配相关接口，包括菜单，模块，互动，数据以及其它权限分配")
public class SysRolePermissionController {

    private final SysRolePermissionApi sysRolePermissionApi;

    @GetMapping("/get-role-menu")
    @Operation(summary = "查询角色菜单", description = "根据角色ID查询该角色关联的菜单列表")
    public ApiResponse<List<RoleMenuResp>> getRoleMenu(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        return ApiResponse.success(sysRolePermissionApi.getRoleMenu(roleId, subjectId, projectNo));
    }

    @GetMapping("/get-role-menu-by-category")
    @Operation(summary = "根据菜单分类查询角色菜单", description = "根据角色ID和菜单分类查询该角色关联的菜单列表")
    public ApiResponse<List<RoleMenuResp>> getRoleMenuByCategory(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId,
            @Parameter(description = "菜单分类.1:业务菜单 2:系统菜单", example = "1")
                    @RequestParam(required = false)
                    Integer category) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        return ApiResponse.success(
                sysRolePermissionApi.getRoleMenuByCategory(roleId, category, subjectId, projectNo));
    }

    @PostMapping("/save-role-menu")
    @Operation(summary = "保存角色菜单", description = "保存角色与菜单的关联关系")
    public ApiResponse<Void> saveRoleMenu(@RequestBody SaveRoleMenuReq request) {
        sysRolePermissionApi.saveRoleMenu(request);
        return ApiResponse.success();
    }

    @GetMapping("/get-role-interaction-permission")
    @Operation(summary = "查询角色交互权限", description = "根据角色ID查询该角色关联的交互权限列表")
    public ApiResponse<List<RoleInteractionPermissionResp>> getRoleInteractionPermission(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        return ApiResponse.success(sysRolePermissionApi.getRoleInteractionPermission(roleId));
    }

    @GetMapping("/get-role-interaction-permission-by-module")
    @Operation(summary = "按模块查询角色交互权限", description = "根据角色ID和模块ID查询该角色关联的交互权限列表，按类型分组返回")
    public ApiResponse<RoleInteractionPermissionByModuleResp> getRoleInteractionPermissionByModule(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId,
            @Parameter(description = "模块ID", example = "1") @RequestParam Long moduleId) {
        return ApiResponse.success(
                sysRolePermissionApi.getRoleInteractionPermissionByModule(roleId, moduleId));
    }

    @PostMapping("/save-role-interaction-permission")
    @Operation(summary = "保存角色交互权限", description = "保存角色与交互权限的关联关系")
    public ApiResponse<Void> saveRoleInteractionPermission(
            @RequestBody SaveRoleInteractionPermissionReq request) {
        sysRolePermissionApi.saveRoleInteractionPermission(request);
        return ApiResponse.success();
    }

    @PostMapping("/save-role-data-permission")
    @Operation(summary = "保存角色数据权限", description = "保存角色与数据权限的关联关系，hasTarget为1时需指定用户列表")
    public ApiResponse<Void> saveRoleDataPermission(
            @RequestBody SaveRoleDataPermissionReq request) {
        sysRolePermissionApi.saveRoleDataPermission(request);
        return ApiResponse.success();
    }

    @GetMapping("/get-role-data-permission")
    @Operation(summary = "查询角色数据权限", description = "根据角色ID查询该角色关联的数据权限列表")
    public ApiResponse<RoleDataPermissionResp> getRoleDataPermission(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        return ApiResponse.success(sysRolePermissionApi.getRoleDataPermission(roleId));
    }

    @GetMapping("/get-role-data-permission-detail")
    @Operation(summary = "查询角色数据权限详情", description = "根据角色ID查询该角色关联的数据权限详情，包含业务数据列表")
    public ApiResponse<List<DataRightConfigResp>> getRoleDataPermissionDetail(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        return ApiResponse.success(sysRolePermissionApi.getRoleDataPermissionDetail(roleId));
    }

    @GetMapping("/get-role-special-permission")
    @Operation(summary = "查询角色特殊权限", description = "根据角色ID查询该角色关联的特殊权限列表")
    public ApiResponse<List<RoleSpecialPermissionResp>> getRoleSpecialPermission(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        return ApiResponse.success(sysRolePermissionApi.getRoleSpecialPermission(roleId));
    }

    @PostMapping("/save-role-special-permission")
    @Operation(summary = "保存角色特殊权限", description = "保存角色与特殊权限的关联关系")
    public ApiResponse<Void> saveRoleSpecialPermission(
            @RequestBody SaveRoleSpecialPermissionReq request) {
        sysRolePermissionApi.saveRoleSpecialPermission(request);
        return ApiResponse.success();
    }

    @PostMapping("/save-role-module-field-permission")
    @Operation(summary = "保存角色模块字段权限", description = "保存角色与模块字段权限的关联关系")
    public ApiResponse<Void> saveRoleModuleFieldPermission(
            @RequestBody SaveRoleModuleFieldPermissionReq request) {
        sysRolePermissionApi.saveRoleModuleFieldPermission(request);
        return ApiResponse.success();
    }

    @GetMapping("/get-role-module-field-permission")
    @Operation(summary = "查询角色模块字段权限列表", description = "根据角色ID和模块类别查询该角色已分配的模块字段权限列表")
    public ApiResponse<List<RoleModuleFieldPermissionResp>> getRoleModuleFieldPermission(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId,
            @Parameter(description = "模块类别 (1=业务模块, 2=系统模块)", example = "1")
                    @RequestParam(required = false)
                    Integer category) {
        return ApiResponse.success(
                sysRolePermissionApi.getRoleModuleFieldPermission(roleId, category));
    }

    @GetMapping("/check-role-permission")
    @Operation(summary = "检查角色是否有模块字段权限", description = "根据角色ID查询是否有模块字段权限，通过上下文获取主体ID和项目编码")
    public ApiResponse<Boolean> checkRoleHasPermission(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        return ApiResponse.success(
                sysRolePermissionApi.checkRoleHasPermission(roleId, subjectId, projectNo));
    }

    @GetMapping("/get-personal-setting-by-role")
    @Operation(
            summary = "查询角色个性化设置",
            description = "根据角色ID查询用户个性化设置（仅包含列表类型模块的表头字段配置）。可选传入模块ID进行过滤")
    public ApiResponse<List<PersonalSettingResp>> getPersonalSettingByRole(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId,
            @Parameter(description = "模块ID（可选，不传则查询所有模块）", example = "1")
                    @RequestParam(required = false)
                    Long moduleId) {
        return ApiResponse.success(sysRolePermissionApi.getPersonalSettingByRole(roleId, moduleId));
    }
}
