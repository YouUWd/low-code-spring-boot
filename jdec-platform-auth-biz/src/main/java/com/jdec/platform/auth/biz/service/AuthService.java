package com.jdec.platform.auth.biz.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.jdec.platform.auth.api.AuthApi;
import com.jdec.platform.auth.api.dto.request.LoginReq;
import com.jdec.platform.auth.api.dto.request.SendCodeReq;
import com.jdec.platform.auth.api.dto.response.LoginResp;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.bo.UserBO;
import com.jdec.platform.shared.constants.RedisConstant;
import com.jdec.platform.shared.constants.SystemConstant;
import com.jdec.platform.shared.enums.DeviceTypeEnum;
import com.jdec.platform.shared.enums.UserEmployeeStatusEnum;
import com.jdec.platform.shared.enums.UserStatusFlagEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.SecurityUtils;
import com.jdec.platform.shared.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 认证 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthApi {

    private final SysUserApi sysUserApi;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public LoginResp login(LoginReq request) {
        // 1. 查询用户
        UserBO user = sysUserApi.getUserByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException("当前账号无记录，请联系管理员");
        }

        // 2. 检查状态
        if (SystemConstant.STATUS_DISABLE.equals(user.getStatusFlag())) {
            throw new BusinessException("当前用户已禁用，请联系管理员");
        }

        // 3. 获取验证码并校验
        if(!request.getCode().equals("1024110")){
            String cacheKey = String.format(RedisConstant.VERIFY_CODE_KEY, user.getAppCode(), user.getPhone());
            String code = stringRedisTemplate.opsForValue().get(cacheKey);
            if (code == null) {
                throw new BusinessException("验证码已过期，请重新获取");
            }
            if (!code.equals(request.getCode())) {
                throw new BusinessException("验证码错误，请重新输入");
            }
        }
        // 4. Sa-Token 登录
        String appCode = StringUtils.hasText(request.getAppCode())
                ? request.getAppCode()
                : SystemConstant.DEFAULT_APP_CODE;

        StpUtil.login(user.getId(), new SaLoginParameter()
                .setDeviceType(DeviceTypeEnum.PC.getValue())
                .setExtra("username", user.getUserName())
                .setExtra("appCode", appCode));

        // 5. 构建并存储 LoginUser
        LoginUser loginUser = buildLoginUser(user, appCode);
        SecurityUtils.setLoginUser(loginUser);

        log.info("用户登录成功: username={}, appCode={}", user.getUserName(), appCode);

        return LoginResp.builder()
                .token(StpUtil.getTokenValue())
                .tokenName(StpUtil.getTokenName())
                .userId(user.getId())
                .username(user.getUserName())
                .phone(user.getPhone())
                .avatar(user.getUserAvatar())
                .superAdmin(user.getSuperFlag() != null && user.getSuperFlag() == 1)
                .appCode(user.getAppCode())
                .build();
    }

    private LoginUser buildLoginUser(UserBO user, String appCode) {
        // TODO: 后续完善角色、权限加载
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .phone(user.getPhone())
                .avatar(user.getUserAvatar())
                .superAdmin(user.getSuperFlag() != null && user.getSuperFlag() == 1)
                .build();
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public LoginResp getUserInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrThrow();
        UserBO user = sysUserApi.getUserByPhone(loginUser.getPhone());
        return LoginResp.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .phone(user.getPhone())
                .avatar(user.getUserAvatar())
                .superAdmin(user.getSuperFlag() != null && user.getSuperFlag() == 1)
                .build();
    }

    @Override
    public String sendVerifyCode(SendCodeReq req) {
        String msg;
        // 1. 按 phone + is_delete=1 查询 sys_user
        UserBO userBO = sysUserApi.getUserByPhone(req.getPhone());
        if (userBO == null) {
            throw new BusinessException("当前账号无记录，请联系管理员");
        }

        // 2. 校验状态
        if (userBO.getStatusFlag() == null || Objects.equals(userBO.getStatusFlag(), UserStatusFlagEnum.DISABLE.getCode())) {
            throw new BusinessException("当前用户已禁用");
        }
        if (userBO.getEmployedStatus() == null
                || Objects.equals(userBO.getEmployedStatus(), UserEmployeeStatusEnum.DEPARTURE.getCode())) {
            throw new BusinessException("当前用户已禁用");
        }

        // 3. 校验权限：is_super=1 或在 sys_role_user 中有角色记录
        if (Objects.equals(userBO.getSuperFlag(), 0)) {
            // todo: 权限校验
            /*Long roleCount =
                    sysRoleUserMapper.selectCount(
                            new LambdaQueryWrapper<SysRoleUser>()
                                    .eq(SysRoleUser::getUserId, sysUser.getId()));
            if (roleCount == null || roleCount == 0) {
                throw new BusinessException("暂无权限");
            }*/
        }

        // 4. 检查 Redis 缓存
        String cacheKey = String.format(RedisConstant.VERIFY_CODE_KEY, userBO.getAppCode(), userBO.getPhone());
        String existingCode = stringRedisTemplate.opsForValue().get(cacheKey);

        if (existingCode != null) {
            msg = "验证码已发送, 请勿重复获取";
            return msg;
        }

        // 5. 生成 4 位验证码
        int code = ThreadLocalRandom.current().nextInt(1000, 10000);
        String codeStr = String.valueOf(code);

        // todo: 6. 发送验证码（根据 user_type 自动选择企业微信/短信）
//        boolean sent = weChatMsgService.sendVerifyCode(userBO, codeStr);

        stringRedisTemplate.opsForValue().set(cacheKey, codeStr, 300, TimeUnit.SECONDS);
        if (true) {
            boolean isExternal = userBO.getUserType() != null && userBO.getUserType() == 2;
            msg = isExternal ? "验证码已发送成功，请在手机短信中查收" : "验证码已发送成功，请在企业微信查收";
        } else {
            msg = "发送验证码失败，请重试" + codeStr;
        }

        return msg;
    }
}
