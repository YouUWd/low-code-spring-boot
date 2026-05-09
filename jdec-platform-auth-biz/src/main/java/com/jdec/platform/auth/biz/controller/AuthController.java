package com.jdec.platform.auth.biz.controller;

import com.jdec.platform.auth.api.AuthApi;
import com.jdec.platform.auth.api.dto.request.LoginReq;
import com.jdec.platform.auth.api.dto.request.SendCodeReq;
import com.jdec.platform.auth.api.dto.response.LoginResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 认证控制器 */
@Tag(name = "认证管理", description = "登录、退出、用户信息")
@RestController
@RequestMapping("/api/config/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApi authApi;

    @PostMapping("/send-verify-code")
    @Operation(summary = "获取验证码", description = "发送登录验证码到指定手机号")
    public ApiResponse<Void> sendVerifyCode(@RequestBody(required = false) SendCodeReq req) {
        return ApiResponse.success(authApi.sendVerifyCode(req), null);
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<LoginResp> login(@Valid @RequestBody LoginReq request) {
        return ApiResponse.success(authApi.login(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authApi.logout();
        return ApiResponse.success();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/userInfo")
    public ApiResponse<LoginResp> getUserInfo() {
        return ApiResponse.success(authApi.getUserInfo());
    }
}
