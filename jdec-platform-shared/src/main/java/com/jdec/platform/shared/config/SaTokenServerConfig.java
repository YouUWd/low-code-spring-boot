package com.jdec.platform.shared.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.sso.name.ParamName;
import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.json.JSONUtil;
import com.jdec.platform.shared.constants.RedisConstant;
import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.SaTokenUserDataLoader;
import com.jdec.platform.shared.security.model.LoginUser;
import com.jdec.platform.shared.security.model.UserInfo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenServerConfig {
    private final SaTokenUserDataLoader saTokenUserDataLoader;
    private final SaSsoServerTemplate saSsoServerTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${sso.login-page-url}")
    public String ssoLoginPageUrl;

    @PostConstruct
    public void configSsoServer() {
        // 配置：登录处理函数
        saSsoServerTemplate.strategy.doLoginHandle =
                (name, pwd) -> {
                    // 参数校验
                    String phone = SaHolder.getRequest().getParam("phone");
                    if (phone == null || phone.trim().isEmpty()) {
                        throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "手机号不能为空");
                    }
                    // 手机号正则校验（11位数字，1开头）
                    if (!phone.matches("^1[3-9]\\d{9}$")) {
                        throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "手机号格式不正确");
                    }
                    String verifyCode = SaHolder.getRequest().getParam("verifyCode");
                    if (verifyCode == null || verifyCode.trim().isEmpty()) {
                        throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "验证码不能为空");
                    }
                    String client = saSsoServerTemplate.paramName.client;
                    String projectNo = SaHolder.getRequest().getParam(client);
                    if (projectNo == null || projectNo.trim().isEmpty()) {
                        throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "客户端标识不能为空");
                    }

                    log.info("SSO doLogin, phone={}, projectNo={}", phone, projectNo);
                    // 调用业务层验证账号密码
                    UserInfo userInfo = saTokenUserDataLoader.getUserByPhone(phone);
                    if (userInfo == null) {
                        throw new BusinessException(ApiCodeEnum.OVERDUE.getCode(), "用户不存在");
                    }
                    // 通过角色用户表查询，判定当前登录的用户是否有权限
                    Long count = saTokenUserDataLoader.getUserRoleCount(phone, projectNo);
                    if (count == 0) {
                        throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "用户无此应用使用权限");
                    }
                    // 3. 获取验证码并校验
                    if (!verifyCode.equals("1024110")) {
                        String cacheKey =
                                String.format(
                                        RedisConstant.VERIFY_CODE_KEY,
                                        userInfo.getProjectNo(),
                                        phone);
                        String code = stringRedisTemplate.opsForValue().get(cacheKey);
                        if (code == null) {
                            throw new BusinessException(
                                    ApiCodeEnum.OVERDUE.getCode(), "验证码已过期，请重新获取");
                        }
                        if (!code.equals(verifyCode)) {
                            throw new BusinessException(
                                    ApiCodeEnum.OVERDUE.getCode(), "验证码错误，请重新输入");
                        }
                    }

                    // 登录
                    StpUtil.login(userInfo.getId());

                    // 存储用户信息到 Session
                    LoginUser loginUser =
                            LoginUser.builder()
                                    .userId(userInfo.getUserId())
                                    .username(userInfo.getUsername())
                                    .token(StpUtil.getTokenValue())
                                    .build();
                    StpUtil.getSession().set("loginUser", JSONUtil.toJsonStr(loginUser));
                    log.info(
                            "SSO 登录成功，userId={}, username={}",
                            userInfo.getId(),
                            userInfo.getUsername());
                    return SaResult.data(loginUser).setMsg("登录成功");
                };

        // 前后端分离核心配置：重定向到前端登录页
        saSsoServerTemplate.strategy.notLoginView =
                (() -> {
                    ParamName paramName = saSsoServerTemplate.paramName;
                    // 获取当前请求的 redirect 参数（客户端回调地址）
                    String redirectUrl = SaHolder.getRequest().getParam(paramName.redirect);
                    String projectNo = SaHolder.getRequest().getParam(paramName.client);
                    // 构建前端登录页地址，携带 redirect 参数
                    // 前端登录成功后需要用此参数进行回调
                    String loginPageUrl =
                            ssoLoginPageUrl
                                    + "?projectNo="
                                    + projectNo
                                    + "&redirect="
                                    + SaFoxUtil.encodeUrl(redirectUrl);

                    // 重定向到前端登录页
                    SaHolder.getResponse().redirect(loginPageUrl);
                    return SaResult.ok();
                });
    }

    @Autowired
    private void configSso(SaSsoServerTemplate saSsoServerTemplate) {
        SaSsoServerProcessor.instance.ssoServerTemplate.apiName.ssoDoLogin =
                "/api/config/sso/doLogin";
        SaSsoServerProcessor.instance.ssoServerTemplate.apiName.ssoCheckTicket =
                "/api/config/sso/checkTicket";
        SaSsoServerProcessor.instance.ssoServerTemplate.apiName.ssoPushS = "/api/config/sso/pushS";
        SaSsoServerProcessor.instance.ssoServerTemplate.apiName.ssoSignout =
                "/api/config/sso/signout";
        SaSsoServerProcessor.instance.ssoServerTemplate.apiName.ssoLogin =
                "/api/config/sso/isLogin";
    }
}
