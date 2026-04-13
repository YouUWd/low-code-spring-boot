package com.jdec.platform.auth.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.auth.api.SysUserApi;
import com.jdec.platform.auth.api.dto.response.LoginResp;
import com.jdec.platform.auth.biz.entity.SysUser;
import com.jdec.platform.auth.biz.mapper.SysUserMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.JwtTokenProvider;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@DataSource(DataSourceConstants.AUTH_CENTER)
public class AuthServiceImpl implements SysUserApi {

    private final SysUserMapper sysUserMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final VerificationCodeService verificationCodeService;

    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @param subjectId 主体ID
     * @param weChatUserId 企业微信用户ID
     */
    @Override
    public void sendCode(String phone, Integer subjectId, String weChatUserId) {
        verificationCodeService.sendCode(phone, subjectId, weChatUserId);
        log.info("验证码已发送至: {}", phone);
    }

    /**
     * 验证码登陆
     *
     * @param phone 手机号
     * @param code 验证码
     * @return 登陆结果
     */
    @Override
    public LoginResp loginWithCode(String phone, String code) {
        // 验证验证码
        if (!verificationCodeService.verifyCode(phone, code)) {
            throw new BusinessException(401, "验证码错误");
        }

        // 根据手机号查找用户
        SysUser user =
                Optional.ofNullable(
                                sysUserMapper.selectOne(
                                        new LambdaQueryWrapper<SysUser>()
                                                .eq(SysUser::getPhone, phone)))
                        .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (user.getStatus() != 1) {
            throw new BusinessException(403, "账户已被禁用，请联系管理员");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUserName());
        log.info("用户验证码登录成功: {}", phone);

        return new LoginResp(
                token, user.getId(), user.getUserName(), user.getEmail(), user.getStatus());
    }
}
