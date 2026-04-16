package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysUserRoleApi;
import com.jdec.platform.config.api.dto.request.AddRoleToUserReq;
import com.jdec.platform.config.api.dto.response.UserRoleEntryResp;
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
 * 用户角色关联 REST 控制器
 *
 * <p>提供用户与角色的关联管理接口。
 */
@RestController
@RequestMapping("/api/config/user-roles")
@RequiredArgsConstructor
@Tag(name = "用户角色关联", description = "用户与角色的关联管理接口")
public class SysUserRoleController {

    private final SysUserRoleApi userRoleService;

    /**
     * 根据用户 ID 查询角色列表
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "查询用户的角色", description = "根据用户 ID 查询该用户拥有的所有角色")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
    })
    public ApiResponse<List<UserRoleEntryResp>> listRolesByUser(
            @Parameter(description = "用户 ID", example = "1")
            @PathVariable Long userId) {
        List<UserRoleEntryResp> roles = userRoleService.getRolesByUserId(userId);
        return ApiResponse.success(roles);
    }

    /**
     * 为用户添加角色
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 添加结果
     */
    @PostMapping("/{userId}/{roleId}")
    @Operation(summary = "为用户添加角色", description = "为指定用户添加角色")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "添加成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<UserRoleEntryResp> addRoleToUser(
            @Parameter(description = "用户 ID", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "角色 ID", example = "1")
            @PathVariable Long roleId) {
        UserRoleEntryResp result = userRoleService.addRoleToUser(userId, roleId);
        return ApiResponse.success("角色添加成功", result);
    }

    /**
     * 从用户移除角色
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 移除结果
     */
    @DeleteMapping("/{userId}/{roleId}")
    @Operation(summary = "从用户移除角色", description = "从指定用户移除角色")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "移除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<Void> removeRoleFromUser(
            @Parameter(description = "用户 ID", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "角色 ID", example = "1")
            @PathVariable Long roleId) {
        userRoleService.removeRoleFromUser(userId, roleId);
        return ApiResponse.success();
    }
}
