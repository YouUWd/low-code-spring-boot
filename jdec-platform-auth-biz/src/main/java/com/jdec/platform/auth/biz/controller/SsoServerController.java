package com.jdec.platform.auth.biz.controller;

import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
import cn.dev33.satoken.sso.template.SaSsoServerUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SSO Server 端控制器 提供单点登录服务端接口 */
@Slf4j
@Tag(name = "SSO单点登录", description = "SSO Server 端接口，供客户端接入使用")
@RestController
@RequestMapping("/api/config/sso")
@RequiredArgsConstructor
public class SsoServerController {
    /**
     * SSO-Server端：处理所有SSO相关请求（兜底接口，保留用于Sa-Token内部路由） http://{host}:{port}/api/config/sso/auth --
     * 单点登录授权地址 http://{host}:{port}/api/config/sso/doLogin -- 账号密码登录接口，接受参数：name、pwd
     * http://{host}:{port}/api/config/sso/signout -- 单点注销地址（isSlo=true时打开）
     */
    @Operation(summary = "SSO通用处理接口", description = "兜底接口，处理所有未明确定义的SSO请求", hidden = true)
    @RequestMapping("/**")
    public Object ssoRequest() {
        return SaSsoServerProcessor.instance.dister();
    }

    /**
     * SSO单点登录授权接口
     *
     * @param redirect 客户端回调地址
     * @param client 客户端标识（应用 projectNo）
     * @return 授权结果（重定向或登录页）
     */
    @GetMapping("/auth")
    @Operation(summary = "SSO单点登录授权", description = "SSO认证入口，已登录返回ticket，未登录跳转登录页")
    public Object auth(
            @Parameter(description = "客户端回调地址", required = true) @RequestParam String redirect,
            @Parameter(description = "客户端标识（应用 projectNo）") @RequestParam(required = false)
                    String client) {
        return SaSsoServerProcessor.instance.dister();
    }

    /**
     * SSO账号密码登录接口
     *
     * @param phone 手机号
     * @param verifyCode 验证码
     * @return 登录结果
     */
    @PostMapping("/doLogin")
    @Operation(summary = "SSO账号密码登录", description = "提交用户名密码进行SSO登录认证")
    public Object doLogin(
            @Parameter(description = "手机号", required = true) @RequestParam String phone,
            @Parameter(description = "验证码", required = true) @RequestParam String verifyCode,
            @Parameter(description = "应用编码", required = true) @RequestParam String client) {
        return SaSsoServerProcessor.instance.dister();
    }

    /**
     * 获取重定向 URL
     *
     * @param client 客户端标识（应用 projectNo）
     * @param redirect 客户端回调地址（需 URL 编码）
     * @return 重定向 URL，未登录返回空字符串
     */
    @GetMapping("/getRedirectUrl")
    @Operation(summary = "获取SSO重定向URL", description = "已登录用户获取携带 ticket 的重定向地址，用于跨域单点登录")
    public ApiResponse<String> getRedirectUrl(
            @Parameter(description = "客户端标识（应用 projectNo）", required = true) String client,
            @Parameter(description = "客户端回调地址（需 URL 编码）", required = true) String redirect) {
        // 未登录情况下，返回空字符串
        if (!StpUtil.isLogin()) {
            return ApiResponse.success("");
        }
        // 已登录情况下，构建 redirectUrl
        redirect = SaFoxUtil.decoderUrl(redirect);
        String redirectUrl =
                SaSsoServerUtil.buildRedirectUrl(
                        client, redirect, StpUtil.getLoginId(), StpUtil.getTokenValue());
        return ApiResponse.success(redirectUrl);
    }

    /** todo: 获取SSO登录页应用信息 前端登录页初始化时调用，根据 projectNo 展示应用信息 */
    /*    @GetMapping("/appInfo")
    @Operation(summary = "获取SSO登录页应用信息")
    public ApiResponse<SsoAppInfoVO> getAppInfo(@RequestParam(name = "projectNo", required = false,
            defaultValue = "SYSTEM") String projectNo) {
        SsoAppInfoVO vo = sysAppService.getSsoAppInfo(projectNo);
        return ApiResponse.success(vo);
    }*/

    /** 当前是否已登录 */
    @GetMapping("/isLogin")
    @Operation(summary = "检查SSO登录状态")
    public ApiResponse<Boolean> isLogin() {
        return ApiResponse.success(StpUtil.isLogin());
    }

    /**
     * 获取 SSO Ticket
     *
     * @param client 客户端标识（应用 projectNo）
     * @return ticket 字符串，未登录返回空字符串
     */
    @GetMapping("/getTicket")
    @Operation(summary = "获取SSO Ticket", description = "已登录用户获取用于跨域认证的 ticket")
    public ApiResponse<String> getTicket(
            @Parameter(description = "客户端标识（应用 projectNo）", required = true) String client) {
        // 未登录情况下，返回空字符串
        if (!StpUtil.isLogin()) {
            log.warn("用户未登录，无法获取 ticket");
            return ApiResponse.success("");
        }
        Object loginId = StpUtil.getLoginId();
        // 删除旧ticket
        SaSsoServerUtil.deleteTicket(
                SaSsoServerUtil.getTicketValue(String.valueOf(loginId), client));
        // 已登录情况下，创建 ticket
        String ticket =
                SaSsoServerUtil.createTicketAndSave(
                        client, String.valueOf(loginId), StpUtil.getTokenValue());
        log.info("为 client={} 创建 ticket 成功，loginId={}", client, loginId);
        return ApiResponse.success(ticket);
    }
}
