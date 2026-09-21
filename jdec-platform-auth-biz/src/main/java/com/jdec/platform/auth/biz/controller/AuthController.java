package com.jdec.platform.auth.biz.controller;

import com.jdec.platform.auth.api.AuthApi;
import com.jdec.platform.auth.api.dto.request.SelectRoleReq;
import com.jdec.platform.auth.api.dto.response.CurrentUserCheckResp;
import com.jdec.platform.auth.api.dto.response.LoginResult;
import com.jdec.platform.auth.api.dto.response.SelectRoleResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@Tag(
        name = "用户登录相关", // 分组名称（文档中显示的分组标签）
        description = "处理用户登录请求、判断用户是否有当前登录系统的权限" // 分组描述
        )
public class AuthController {

    private final AuthApi authService;

    /**
     * 用户登录。
     *
     * <p>本端点被 {@code WebMvcConfig} 排除在 {@code JwtAuthenticationInterceptor} 之外，因此没有拦截器帮忙写 {@link
     * TokenContext}。原先这里靠 {@code authService.extractToken(...)} 自行写入，现在这一步已下移到 {@code
     * AuthServiceImpl.login(...)} 内部（顺带消除了「控制器与服务各剥一次 Bearer 前缀」的重复）， 所以 {@link AuthApi} 上不再暴露
     * {@code extractToken}。
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录")
    public ApiResponse<LoginResult> login(
            @Schema(description = "用户token，在header中传递。")
                    @RequestHeader(name = "Authorization", required = false)
                    String token) {
        return ApiResponse.success(authService.login(token));
    }

    @PostMapping("/permission/check-current-user")
    @Operation(summary = "检查当前用户在本系统是否有角色", description = "检查当前用户在本系统是否有角色")
    public ApiResponse<CurrentUserCheckResp> checkCurrentUser(
            @RequestHeader("JWT-TOKEN") String token) {
        try {
            TokenContext.setToken(token);
            return ApiResponse.success(authService.getCurrentUserCheckInfo(token));
        } finally {
            TokenContext.clear();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "用户退出登录", description = "用户退出登录")
    public ApiResponse<Boolean> logout(
            @Schema(description = "用户token，在header中传递。")
                    @RequestHeader(name = "Authorization", required = false)
                    String token) {
        return ApiResponse.success(authService.logout(token));
    }

    /**
     * 切换角色：返回用户的主体列表与对应菜单树，并把最终选中的主体/角色写入 Redis。
     *
     * <p>与源项目 {@code SysConfigController.selectRole} 一致：请求体中的 {@code subjectId} / {@code roleId}
     * 允许为空，为空时分别回落到请求头携带的当前主体（{@link AppContext}）与当前角色（{@link UserContext}）。 注意服务层已返回 {@link
     * ApiResponse}，此处<b>不要</b>再包一层 {@code ApiResponse.success(...)}。
     */
    @PostMapping("/config/select/role")
    @Operation(summary = "切换角色列表", description = "切换角色列表")
    public ApiResponse<SelectRoleResp> selectRole(
            @RequestBody(required = false) SelectRoleReq req) {
        if (req == null) {
            req = new SelectRoleReq();
        }
        if (req.getSubjectId() == null) {
            Long subjectId = AppContext.getSubjectId();
            if (subjectId != null) {
                req.setSubjectId(subjectId);
            }
        }
        if (req.getRoleId() == null) {
            Long roleId = UserContext.getRoleId();
            if (roleId != null) {
                req.setRoleId(roleId);
            }
        }
        return authService.selectRole(req);
    }
}
