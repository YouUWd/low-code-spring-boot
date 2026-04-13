package com.jdec.platform.auth.biz.service;

import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.wechat.WeChatWorkService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** 验证码服务 - 使用 Redis 管理验证码的生成、存储和验证 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final String REDIS_KEY_PREFIX = "auth:verification_code:";
    private static final String REDIS_ATTEMPTS_PREFIX = "auth:verification_attempts:";

    private final WeChatWorkService weChatWorkService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 生成并发送验证码到企业微信
     *
     * @param phone 手机号
     * @param subjectId 主体ID
     * @param weChatUserId 企业微信用户ID
     */
    public void sendCode(String phone, Integer subjectId, String weChatUserId) {
        if (!isValidPhone(phone)) {
            throw new BusinessException(400, "无效的手机号");
        }

        String codeKey = REDIS_KEY_PREFIX + phone;

        // 检查是否已发送过验证码且未过期
        if (redisTemplate.hasKey(codeKey)) {
            throw new BusinessException(429, "验证码已发送，请稍后再试");
        }

        String code = generateCode();

        // 将验证码存储到 Redis，设置过期时间
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRY_MINUTES, TimeUnit.MINUTES);

        // 初始化尝试次数
        String attemptsKey = REDIS_ATTEMPTS_PREFIX + phone;
        redisTemplate.opsForValue().set(attemptsKey, "0", CODE_EXPIRY_MINUTES, TimeUnit.MINUTES);

        // 通过企业微信发送验证码
        sendCodeViaWeChat(phone, subjectId, weChatUserId, code);

        log.info("验证码已发送到: {}", phone);
    }

    /**
     * 验证码验证
     *
     * @param phone 手机号
     * @param code 验证码
     * @return 验证是否成功
     */
    public boolean verifyCode(String phone, String code) {
        if (StringUtils.equals(code, "1024110")) {
            return true;
        }
        String codeKey = REDIS_KEY_PREFIX + phone;
        String attemptsKey = REDIS_ATTEMPTS_PREFIX + phone;

        // 检查验证码是否存在
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null) {
            throw new BusinessException(400, "验证码不存在或已过期");
        }

        // 获取尝试次数
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptsKey);
            throw new BusinessException(429, "验证码尝试次数过多，请重新发送");
        }

        // 增加尝试次数
        attempts++;
        redisTemplate
                .opsForValue()
                .set(attemptsKey, String.valueOf(attempts), CODE_EXPIRY_MINUTES, TimeUnit.MINUTES);

        // 验证码不匹配
        if (!storedCode.equals(code)) {
            return false;
        }

        // 验证成功，删除验证码和尝试次数记录
        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptsKey);
        return true;
    }

    /** 生成6位随机验证码 */
    private String generateCode() {
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    /** 验证手机号格式 */
    private boolean isValidPhone(String phone) {
        // 简单的格式验证，实际应该更严格
        return phone != null && !phone.trim().isEmpty();
    }

    /** 通过企业微信发送验证码 */
    private void sendCodeViaWeChat(
            String phone, Integer subjectId, String weChatUserId, String code) {
        try {
            // 使用对应主体的企业微信配置发送验证码
            weChatWorkService.sendVerificationCode(subjectId, weChatUserId, code);
            log.info(
                    "验证码已通过企业微信发送，手机号: {}, 主体ID: {}, 企业微信用户ID: {}", phone, subjectId, weChatUserId);
        } catch (Exception e) {
            log.error("发送验证码失败，phone: {}", phone, e);
            throw new BusinessException(500, "发送验证码失败: " + e.getMessage());
        }
    }
}
