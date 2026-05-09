package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.dto.request.SaveRoleMenuReq;
import com.jdec.platform.config.api.dto.response.RoleMenuResp;
import com.jdec.platform.shared.context.AppCodeContext;
import com.jdec.platform.shared.context.SubjectContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config/sys/role")
@RequiredArgsConstructor
@Tag(name = "角色权限管理", description = "角色权限分配相关接口，包括菜单，模块，互动，数据以及其它权限分配")
public class SysRolePermissionController {

    private final SysRoleApi sysRoleApi;

    @GetMapping("/get-role-menu")
    @Operation(summary = "查询角色菜单", description = "根据角色ID查询该角色关联的菜单列表")
    public ApiResponse<List<RoleMenuResp>> getRoleMenu(
            @Parameter(description = "角色ID", example = "1")
            @RequestParam Long roleId
    ) {
        Long subjectId = SubjectContext.get();
        String appCode = AppCodeContext.get();
        return ApiResponse.success(sysRoleApi.getRoleMenu(roleId, subjectId, appCode));
    }

    @PostMapping("/save-role-menu")
    @Operation(summary = "保存角色菜单", description = "保存角色与菜单的关联关系")
    public ApiResponse<Void> saveRoleMenu(@RequestBody SaveRoleMenuReq request) {
        sysRoleApi.saveRoleMenu(request);
        return ApiResponse.success();
    }
}
