package com.jdec.platform.shared.wechat;

import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.wechat.model.AccessTokenResponse;
import com.jdec.platform.shared.wechat.model.MessageRequest;
import com.jdec.platform.shared.wechat.model.WeChatWorkResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** 企业微信服务 - 支持多主体 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatWorkService {

    private final WeChatWorkConfig config;
    private final RestTemplate restTemplate;

    // 缓存的access_token，按主体ID分别缓存
    private final Map<Long, TokenCache> tokenCacheMap = new ConcurrentHashMap<>();

    /** Token缓存 */
    private static class TokenCache {
        String accessToken;
        long expiryTime;

        TokenCache(String accessToken, long expiryTime) {
            this.accessToken = accessToken;
            this.expiryTime = expiryTime;
        }

        boolean isValid() {
            return accessToken != null && System.currentTimeMillis() < expiryTime;
        }
    }

    /**
     * 获取access_token
     *
     * @param subjectId 主体ID
     */
    public String getAccessToken(Long subjectId) {
        // 检查缓存是否有效
        TokenCache cache = tokenCacheMap.get(subjectId);
        if (cache != null && cache.isValid()) {
            return cache.accessToken;
        }

        synchronized (this) {
            // 双重检查
            cache = tokenCacheMap.get(subjectId);
            if (cache != null && cache.isValid()) {
                return cache.accessToken;
            }

            WeChatWorkConfig.SubjectConfig subjectConfig = config.getSubjectConfig(subjectId);
            if (subjectConfig == null) {
                throw new BusinessException(500, "未找到主体ID " + subjectId + " 的企业微信配置");
            }

            String url =
                    String.format(
                            "%s/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
                            config.getApiBaseUrl(),
                            subjectConfig.getCorpId(),
                            subjectConfig.getCorpSecret());

            try {
                ResponseEntity<AccessTokenResponse> response =
                        restTemplate.getForEntity(url, AccessTokenResponse.class);

                AccessTokenResponse body = response.getBody();
                if (body == null || !body.isSuccess()) {
                    throw new BusinessException(
                            500,
                            "获取企业微信access_token失败: " + (body != null ? body.getErrMsg() : "响应为空"));
                }

                long expiryTime =
                        System.currentTimeMillis()
                                + TimeUnit.SECONDS.toMillis(config.getTokenCacheSeconds());
                tokenCacheMap.put(subjectId, new TokenCache(body.getAccessToken(), expiryTime));

                log.info("成功获取主体{}的企业微信access_token，有效期: {}秒", subjectId, body.getExpiresIn());
                return body.getAccessToken();

            } catch (Exception e) {
                log.error("获取主体{}的企业微信access_token失败", subjectId, e);
                throw new BusinessException(500, "获取企业微信access_token失败: " + e.getMessage());
            }
        }
    }

    /**
     * 发送文本消息
     *
     * @param subjectId 主体ID
     * @param userId 用户ID（企业微信用户ID）
     * @param content 消息内容
     */
    public void sendTextMessage(Long subjectId, String userId, String content) {
        sendMessage(subjectId, userId, content, "text");
    }

    /**
     * 发送Markdown消息
     *
     * @param subjectId 主体ID
     * @param userId 用户ID（企业微信用户ID）
     * @param content 消息内容（支持Markdown语法）
     */
    public void sendMarkdownMessage(Long subjectId, String userId, String content) {
        sendMessage(subjectId, userId, content, "markdown");
    }

    /**
     * 统一发送消息入口
     *
     * @param subjectId 主体ID
     * @param userId 用户ID
     * @param content 内容
     * @param msgType 消息类型 (text, markdown等)
     */
    public void sendMessage(Long subjectId, String userId, String content, String msgType) {
        MessageRequest.MessageRequestBuilder builder =
                MessageRequest.builder().toUser(userId).msgType(msgType);

        if ("markdown".equalsIgnoreCase(msgType)) {
            builder.markdown(MessageRequest.MarkdownContent.builder().content(content).build());
        } else {
            builder.text(MessageRequest.TextContent.builder().content(content).build());
        }

        executeSendMessage(subjectId, userId, builder.build());
    }

    /** 执行发送逻辑 */
    private void executeSendMessage(Long subjectId, String userId, MessageRequest request) {
        WeChatWorkConfig.SubjectConfig subjectConfig = config.getSubjectConfig(subjectId);
        if (subjectConfig == null) {
            throw new BusinessException(500, "未找到主体ID " + subjectId + " 的企业微信配置");
        }

        request.setAgentId(subjectConfig.getAgentId());

        String accessToken = getAccessToken(subjectId);
        String url =
                String.format(
                        "%s/cgi-bin/message/send?access_token=%s",
                        config.getApiBaseUrl(), accessToken);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MessageRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<WeChatWorkResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, WeChatWorkResponse.class);

            WeChatWorkResponse body = response.getBody();
            if (body == null || !body.isSuccess()) {
                throw new BusinessException(
                        500, "发送企业微信消息失败: " + (body != null ? body.getErrMsg() : "响应为空"));
            }

            log.info("成功发送企业微信{}消息到主体{}的用户: {}", request.getMsgType(), subjectId, userId);

        } catch (Exception e) {
            log.error("发送企业微信{}消息失败，主体: {}, 用户: {}", request.getMsgType(), subjectId, userId, e);
            throw new BusinessException(500, "发送企业微信消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送验证码消息
     *
     * @param subjectId 主体ID
     * @param userId 用户ID（企业微信用户ID）
     * @param code 验证码
     */
    public void sendVerificationCode(Long subjectId, String userId, String code) {
        String content = String.format("【验证码】您的登录验证码是：%s，有效期5分钟，请勿泄露给他人。如非本人操作，请忽略此消息。", code);
        sendTextMessage(subjectId, userId, content);
    }

    /**
     * 发送Markdown格式的验证码消息
     *
     * @param projectNo 项目编号
     * @param projectName 项目名称
     * @param userId 用户ID
     * @param code 验证码
     * @param userName 用户姓名
     */
    public void sendMarkdownVerificationCode(
            Long subjectId,
            String projectNo,
            String projectName,
            String userId,
            String code,
            String userName) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String content =
                String.format(
                        """
                ## %s
                <font color="comment">%s</font>
                > #### 验证码: <font color='warning'>%s</font>
                 #### 验证码5分钟内有效期,请勿泄露他人使用。
                #### 消息接收人:%s
                """,
                        projectName, now, code, userName);
        sendMarkdownMessage(subjectId, userId, content);
    }
}
