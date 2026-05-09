package com.jdec.platform.auth.biz.controller;

import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
import cn.dev33.satoken.sso.template.SaSsoServerUtil;
import cn.dev33.satoken.sso.util.SaSsoConsts;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SSO Server 端控制器 提供单点登录服务端接口 */
@Slf4j
@Tag(name = "SSO单点登录", description = "SSO Server 端接口，供客户端接入使用")
@RestController
@RequestMapping("/api/config/sso")
@RequiredArgsConstructor
public class SsoServerController {
    /**
     * SSO-Server端：处理所有SSO相关请求 http://{host}:{port}/api/config/sso/auth -- 单点登录授权地址
     * http://{host}:{port}/api/config/sso/doLogin -- 账号密码登录接口，接受参数：name、pwd
     * http://{host}:{port}/api/config/sso/signout -- 单点注销地址（isSlo=true时打开）
     */
    @RequestMapping("/**")
    public Object ssoRequest() {
        return SaSsoServerProcessor.instance.dister();
    }

    /** 获取 redirectUrl */
    @RequestMapping("/getRedirectUrl")
    public ApiResponse<String> getRedirectUrl(String client, String redirect, String mode) {
        // 未登录情况下，返回空字符串
        if (!StpUtil.isLogin()) {
            return ApiResponse.success("");
        }
        // 已登录情况下，构建 redirectUrl
        redirect = SaFoxUtil.decoderUrl(redirect);
        if (SaSsoConsts.MODE_SIMPLE.equals(mode)) {
            // 模式一
            SaSsoServerUtil.checkRedirectUrl(client, redirect);
            return ApiResponse.success(redirect);
        } else {
            // 模式二或模式三
            String redirectUrl =
                    SaSsoServerUtil.buildRedirectUrl(
                            client, redirect, StpUtil.getLoginId(), StpUtil.getTokenValue());
            return ApiResponse.success(redirectUrl);
        }
    }

    /** todo: 获取SSO登录页应用信息 前端登录页初始化时调用，根据 appCode 展示应用信息 */
    /*    @GetMapping("/appInfo")
    @Operation(summary = "获取SSO登录页应用信息")
    public ApiResponse<SsoAppInfoVO> getAppInfo(@RequestParam(name = "appCode", required = false,
            defaultValue = "SYSTEM") String appCode) {
        SsoAppInfoVO vo = sysAppService.getSsoAppInfo(appCode);
        return ApiResponse.success(vo);
    }*/

    /** 当前是否已登录 */
    @GetMapping("/isLogin")
    @Operation(summary = "检查SSO登录状态")
    public ApiResponse<Boolean> isLogin() {
        return ApiResponse.success(StpUtil.isLogin());
    }
}
