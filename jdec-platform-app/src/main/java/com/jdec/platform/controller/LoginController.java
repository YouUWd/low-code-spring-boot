package com.jdec.platform.controller;

import com.jdec.platform.auth.api.SysUserApi;
import com.jdec.platform.auth.api.dto.request.LoginReq;
import com.jdec.platform.auth.api.dto.request.SendCodeReq;
import com.jdec.platform.auth.api.dto.response.EmployeeInfoResp;
import com.jdec.platform.auth.api.dto.response.LoginResp;
import com.jdec.platform.auth.api.dto.response.UnifiedLoginResp;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.dto.response.UserBasicInfoResp;
import com.jdec.platform.shared.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 登录控制器 - 应用层协调
 *
 * <p>在应用层组合调用 auth.api 和 hr.api，避免模块间的直接依赖
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final SysUserApi authApi;
    private final UserApi userApi;

    /**
     * 发送验证码
     *
     * @param request 发送验证码请求
     * @return 成功消息
     */
    @PostMapping("/code/send")
    public ApiResponse<String> sendCode(@RequestBody SendCodeReq request) {
        String phone = request.getPhone();

        // 第一步：从 hr.api 获取用户的企业微信信息
        Integer subjectId =
                userApi.getSubjectIdByPhone(phone)
                        .orElseThrow(() -> new RuntimeException("未找到手机号对应的用户: " + phone));

        String weChatUserId =
                userApi.getWeChatUserIdByPhone(phone)
                        .orElseThrow(() -> new RuntimeException("用户未绑定企业微信账号，手机号: " + phone));

        // 第二步：调用 auth.api 发送验证码
        authApi.sendCode(phone, subjectId, weChatUserId);

        return ApiResponse.success("验证码已发送");
    }

    /**
     * 统一登录接口
     *
     * <p>通过 includeEmployeeInfo 参数控制是否返回员工信息
     *
     * @param request 登录请求，包含 phone、code、includeEmployeeInfo
     * @return 登录响应
     */
    @PostMapping("/login")
    public ApiResponse<UnifiedLoginResp> login(@RequestBody LoginReq request) {
        String phone = request.getPhone();
        String code = request.getCode();
        Boolean includeEmployeeInfo = request.getIncludeEmployeeInfo();

        // 第一步：调用 auth.api 进行认证
        LoginResp authResponse = authApi.loginWithCode(phone, code);

        // 第二步：根据参数决定是否获取员工信息
        EmployeeInfoResp employeeInfo = null;
        if (Boolean.TRUE.equals(includeEmployeeInfo)) {
            UserBasicInfoResp basicInfo = userApi.getUserBasicInfoByPhone(phone).orElse(null);
            if (basicInfo != null) {
                employeeInfo =
                        EmployeeInfoResp.builder()
                                .id(basicInfo.getId())
                                .userName(basicInfo.getUserName())
                                .phone(basicInfo.getPhone())
                                .subjectId(basicInfo.getSubjectId())
                                .shortName(basicInfo.getShortName())
                                .workNumber(basicInfo.getWorkNumber())
                                .departmentName(basicInfo.getDepartmentName())
                                .userStatus(basicInfo.getUserStatus())
                                .build();
            }
        }

        // 第三步：组合返回结果
        UnifiedLoginResp response =
                UnifiedLoginResp.builder()
                        .token(authResponse.getToken())
                        .userId(authResponse.getUserId())
                        .username(authResponse.getUsername())
                        .email(authResponse.getEmail())
                        .status(authResponse.getStatus())
                        .employeeInfo(employeeInfo)
                        .build();

        log.info("用户登录成功: {}, includeEmployeeInfo: {}", phone, includeEmployeeInfo);
        return ApiResponse.success(response);
    }
}
