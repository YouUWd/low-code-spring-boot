package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/sys/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户的管理接口")
public class SysUserController {

    private final SysUserApi sysUserApi;

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
}
