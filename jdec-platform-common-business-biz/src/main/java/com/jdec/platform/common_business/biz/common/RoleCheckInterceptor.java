package com.jdec.platform.common_business.biz.common;

import com.jdec.platform.common_business.api.SystemManageServiceApi;
import com.jdec.platform.common_business.api.dto.response.RoleResp;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.configcenter.requst.CheckUserSpecialPermissionReq;
import com.jdec.platform.common_business.biz.dto.configcenter.response.CheckUserSpecialPermissionResp;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RoleCheckInterceptor implements HandlerInterceptor {

    @Autowired private ConfigCenterApiService configCenterApiService;

    @Autowired private SystemManageServiceApi systemManageService;

    @Autowired private ConfigCenterApiOpenFeignClient configCenterApiClient;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        Long roleId = LoginUserUtil.getRoleId();

        Long subjectId = LoginUserUtil.getSubjectIdFromHeader();

        Long userId = LoginUserUtil.getLoginUserId();

        Long realUserId = LoginUserUtil.getRealLoginUserId();

        List<RoleResp> roleRespList =
                configCenterApiService.getRoleListBySubjectAndUserId(subjectId, userId);

        if (CollectionUtils.isEmpty(roleRespList)
                || !roleRespList.stream().anyMatch(ele -> roleId.equals(ele.getRoleId()))) {
            log.debug("当前使用用户id:{}已不属于当前角色id:{}", userId, roleId);
            throw new BusinessException(
                    ApiResponseCodeEnum.CHECK_USER_ROLE_ERROR.getCode(),
                    ApiResponseCodeEnum.CHECK_USER_ROLE_ERROR.getMessage());
        }

        if (!Objects.equals(realUserId, userId)) {
            log.debug("当前登录用户id:{}在使用模拟功能，模拟人用户id:{} , 检查登录用户的模拟权限还是否存在", realUserId, userId);
            // 这个地方无论如何  最终的 主题ID 和 RoleID 都已经拿到了
            ConfigApiResponse<CheckUserSpecialPermissionResp> checkUserSpecialResponse =
                    configCenterApiClient.checkUserSpecialPermission(
                            subjectId,
                            CheckUserSpecialPermissionReq.builder()
                                    .userId(realUserId)
                                    .rightCode(
                                            CommonConstant.CONFIG_CENTER_SIMULATE_LOGIN_RIGHT_CODE)
                                    .build());
            if (ConfigApiResponse.isSuccessResponse(checkUserSpecialResponse)
                    && checkUserSpecialResponse.getData() != null
                    && checkUserSpecialResponse.getData().getHasPermission() != null
                    && checkUserSpecialResponse.getData().getHasPermission()) {
                log.debug("当前登录用户id:{}在使用模拟功能，模拟人用户id:{} , 用户的模拟权限还存在", realUserId, userId);
            } else {
                log.debug("当前登录用户id:{}无模拟权限", realUserId);
                throw new BusinessException(
                        ApiResponseCodeEnum.USER_NO_MOCK_RIGHT.getCode(),
                        ApiResponseCodeEnum.USER_NO_MOCK_RIGHT.getMessage());
            }
        }

        return true;
    }
}
