package com.jdec.platform.common_business.biz.auth.service.impl;

import com.jdec.platform.common_business.biz.auth.Common.AuthCommonConstant;
import com.jdec.platform.common_business.biz.auth.dto.resp.CurrentUserCheckResp;
import com.jdec.platform.common_business.biz.auth.dto.resp.LoginResult;
import com.jdec.platform.common_business.biz.auth.dto.resp.SysInfoDTO;
import com.jdec.platform.common_business.biz.auth.dto.resp.UserInfo;
import com.jdec.platform.common_business.biz.auth.security.JwtTokenProvider;
import com.jdec.platform.common_business.biz.auth.service.AuthApi;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.LoginUserPrincipal;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.dto.configcenter.response.UserCheckResp;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthApi {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final ConfigCenterApiOpenFeignClient configCenterApiClient;

    @Value("${app.internal.url}")
    private String internalUrl;

    @Override
    public CurrentUserCheckResp getCurrentUserCheckInfo(String token) {
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
        }

        String phone = jwtTokenProvider.getUserPhone(token);

        if (StringUtils.isBlank(phone)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
        }

        ConfigApiResponse<UserCheckResp> response =
                configCenterApiClient.getCurrentUserCheck(phone);
        if (ConfigApiResponse.isSuccessResponse(response)) {

            SysInfoDTO sysInfoDTO = new SysInfoDTO();
            sysInfoDTO.setTitle("九鼎昊天学生管理系统");
            sysInfoDTO.setLogo(
                    "http://119.36.78.123:9077/storage/upload/LIdqTCsyOZIqYJ4fsxeBhntUy860blvL8ZSbN7ic.svg");
            sysInfoDTO.setIcon(
                    "http://119.36.78.123:9077/storage/upload/PY9vYNYUdPfhjDKJQa0tiAeLjyTP1KrgSK5w4OTU.svg");
            sysInfoDTO.setIsSubject(2);
            sysInfoDTO.setSubjectColor("rgb(29, 120, 250)");
            sysInfoDTO.setLoginBgImg(
                    "http://119.36.78.123:9077/storage/upload/CB5c5JVPOEIBoK6LKBL5PzZEll5dE27tD2AFU3uR.png");

            sysInfoDTO.setInternalUrl(internalUrl);

            return CurrentUserCheckResp.builder()
                    .hasPermission(response.getData().getPermissionFlag())
                    .sysInfo(sysInfoDTO)
                    .build();
        } else {
            log.debug("系统设置检查当前用户是否有角色接口调用失败,状态吗:{}", response.getStatus());
            return CurrentUserCheckResp.builder().hasPermission(false).build();
        }
    }

    public LoginResult login(String authHeader) {

        if (StringUtils.isBlank(authHeader)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
        }

        String token = extractToken(authHeader);

        if (StringUtils.isNotBlank(token)) {
            String phone = jwtTokenProvider.getUserPhone(token);

            // 校验用户是否已经在大系统登录
            if (!checkUserToken(token, phone)) {
                log.debug("token:{}已过期，手机号:{}请重新登录", token, phone);
                throw new BusinessException(
                        ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                        ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
            }

            if (StringUtils.isBlank(phone)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                        ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
            }
            log.debug("用户手机号:{}登录成功。", phone);

            ConfigApiResponse<UserCheckResp> response =
                    configCenterApiClient.getCurrentUserCheck(phone);
            if (ConfigApiResponse.isSuccessResponse(response)) {
                UserCheckResp userCheckResp = response.getData();

                LoginUserPrincipal loginUserPrincipal = new LoginUserPrincipal();
                loginUserPrincipal.setUserId(userCheckResp.getUserId());
                loginUserPrincipal.setUserName(userCheckResp.getUserName());
                loginUserPrincipal.setSubjectId(userCheckResp.getSubjectId().longValue());
                try {
                    String key = AuthCommonConstant.LOGIN_KEY_PREFIX + phone;
                    redisTemplate.opsForValue().set(key, JsonUtil.obj2Json(loginUserPrincipal));
                } catch (Exception e) {
                    log.error("login redis save user info error,e=", e);
                    throw new BusinessException(
                            ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                            ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                            e);
                }
                return new LoginResult(
                        token,
                        UserInfo.builder()
                                .id(userCheckResp.getUserId())
                                .username(userCheckResp.getUserName())
                                .status(userCheckResp.getStatusFlag())
                                .subjectId(userCheckResp.getSubjectId())
                                .phone(phone)
                                .departId(userCheckResp.getDeptIds())
                                .departName(userCheckResp.getDepartName())
                                .build());
            } else {
                log.debug("调用系统配置获取登录用户信息失败。");
                throw new BusinessException(
                        ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                        ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
            }

        } else {
            throw new BusinessException(
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                    ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
        }
    }

    /** 从 Authorization 请求头中提取 Bearer Token。 */
    @Override
    public String extractToken(String headString) {
        if (org.springframework.util.StringUtils.hasText(headString)
                && headString.startsWith(BEARER_PREFIX)) {
            return headString.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    public Boolean checkUserToken(String token, String phone) {
        try {
            String key = "token" + ":" + phone;
            String value = redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotEmpty(value) && token.equals(value)) {
                return Boolean.TRUE;
            }
        } catch (Exception e) {
            log.error("checkUserToken error,e=", e);
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean removeUserToken(String token, String phone) {
        try {
            String key = "token" + ":" + phone;
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("removeUserToken error,e=", e);
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean removeUserLogin(String token, String phone) {
        try {
            String key = AuthCommonConstant.LOGIN_KEY_PREFIX + phone;
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("remove user info error,e=", e);
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean logout(String tokenHeader) {

        if (StringUtils.isBlank(tokenHeader)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        String token = extractToken(tokenHeader);
        if (StringUtils.isBlank(tokenHeader)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        String phone = jwtTokenProvider.getUserPhone(token);
        if (StringUtils.isEmpty(phone)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        removeUserLogin(token, phone);
        return removeUserToken(token, phone);
    }

    /** 辅助方法：安全地将字符串转换为 Long，避免 NumberFormatException */
    private static Long parseLongSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            // 解析失败时返回 null（或者你可以视业务需求抛出 400 Bad Request 参数异常）
            return null;
        }
    }
}
