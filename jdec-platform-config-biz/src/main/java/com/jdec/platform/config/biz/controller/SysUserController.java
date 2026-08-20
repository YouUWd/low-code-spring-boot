package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.CheckUserPermissionByPhoneResp;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.hr.api.bo.SubjectBO;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/sys/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户的管理接口")
public class SysUserController {

    private final SysUserApi sysUserApi;
    private final SubjectApi subjectApi;

    @GetMapping("/page")
    @Operation(summary = "分页查询用户列表", description = "支持按用户名、工号、手机号、状态等条件分页查询")
    public ApiResponse<PageResult<SysUserPageResp>> getUserPage(QuerySysUserPageReq query) {
        return ApiResponse.success(sysUserApi.getUserPage(query));
    }

    @PutMapping("/edit")
    @Operation(summary = "编辑用户信息", description = "编辑用户基本信息，ID不能为空")
    public ApiResponse<Void> editUser(@Valid @RequestBody UpdateSysUserReq req) {
        sysUserApi.editUser(req);
        return ApiResponse.success();
    }

    @PutMapping("/disable/{id}")
    @Operation(summary = "停用用户", description = "将用户状态设为禁用")
    public ApiResponse<Void> disableUser(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id) {
        sysUserApi.disableUser(id);
        return ApiResponse.success();
    }

    @GetMapping("/list")
    @Operation(
            summary = "查询用户列表",
            description = "先查sys_role_user表根据projectNo过滤拿到用户id，再查sys_user表获取用户数据")
    public ApiResponse<List<SysUserPageResp>> getUserList() {
        return ApiResponse.success(sysUserApi.getUserList());
    }

    @GetMapping("/role/{roleId}")
    @Operation(summary = "根据角色ID查询用户列表", description = "查询指定角色关联的所有有效用户")
    public ApiResponse<List<SysUserPageResp>> getUsersByRoleId(
            @Parameter(description = "角色ID", required = true, example = "1") @PathVariable
                    Long roleId) {
        return ApiResponse.success(sysUserApi.getUsersByRoleId(roleId));
    }

    @PostMapping("/list-by-ids")
    @Operation(summary = "根据用户ID列表批量查询用户", description = "根据sys_user表的user_id字段批量查询用户")
    public ApiResponse<List<SysUserPageResp>> getUsersByUserIds(
            @Valid @RequestBody QueryUsersByIdsReq req) {
        return ApiResponse.success(sysUserApi.getUsersByUserIds(req));
    }

    @GetMapping("/subjects/{userId}")
    @Operation(summary = "根据用户ID查询关联主体列表", description = "根据用户ID反查角色，再反查角色关联的主体ID，最后查询HR模块的主体信息返回")
    public ApiResponse<List<SubjectBO>> getSubjectsByUserId(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable
                    Long userId) {
        List<Long> subjectIds = sysUserApi.getSubjectIdsByUserId(userId);
        if (subjectIds.isEmpty()) {
            return ApiResponse.success(List.of());
        }
        Set<Long> subjectIdSet = new HashSet<>(subjectIds);
        List<SubjectBO> subjects =
                subjectApi.getSubjectList().stream()
                        .filter(s -> subjectIdSet.contains(s.getId()))
                        .toList();
        return ApiResponse.success(subjects);
    }

    @GetMapping("/check-permission")
    @Operation(
            summary = "通过手机号检查用户权限",
            description =
                    "检查逻辑：1. 先查sys_user表 2. 未查到则调用人力UserApi 3. 根据用户subjectId查sys_role_user表并校验时效性")
    public ApiResponse<CheckUserPermissionByPhoneResp> checkUserPermissionByPhone(
            @Parameter(description = "手机号", required = true, example = "13800138000") @RequestParam
                    String phone) {
        CheckUserPermissionByPhoneResp resp = sysUserApi.checkUserPermissionByPhone(phone);
        return ApiResponse.success(resp);
    }

    @GetMapping("/detail/{userId}")
    @Operation(
            summary = "根据用户ID获取用户详情",
            description =
                    "检查逻辑：1. 先查sys_user表 2. 未查到则调用人力UserApi 3. 校验权限（系统设置或主体设置），无权限则抛出异常 4. 返回用户详情")
    public ApiResponse<UserDetailResp> getUserDetailByUserId(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable
                    Long userId) {
        return ApiResponse.success(sysUserApi.getUserDetailByUserId(userId));
    }
}
