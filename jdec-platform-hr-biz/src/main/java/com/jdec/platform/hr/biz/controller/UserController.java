package com.jdec.platform.hr.biz.controller;

import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.hr.api.dto.request.CreateUserReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserReq;
import com.jdec.platform.hr.api.dto.response.UserBasicInfoResp;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理 REST 控制器 */
@Slf4j
@RestController
@RequestMapping("/api/hr/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApi userApi;

    /**
     * 根据手机号获取用户基本信息
     *
     * @param phone 手机号
     * @return 用户基本信息
     */
    @GetMapping("/by-phone")
    public ApiResponse<UserBasicInfoResp> getUserByPhone(@RequestParam String phone) {
        return userApi.getUserBasicInfoByPhone(phone)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "用户不存在"));
    }

    /**
     * 创建用户
     *
     * @param request 创建用户请求
     * @return 用户基本信息
     */
    @PostMapping
    public ApiResponse<UserBasicInfoResp> createUser(@RequestBody CreateUserReq request) {
        UserBasicInfoResp result = userApi.createUser(request);
        return ApiResponse.success("创建成功", result);
    }

    /**
     * 更新用户
     *
     * @param request 更新用户请求
     * @return 用户基本信息
     */
    @PutMapping
    public ApiResponse<UserBasicInfoResp> updateUser(@RequestBody UpdateUserReq request) {
        UserBasicInfoResp result = userApi.updateUser(request);
        return ApiResponse.success("更新成功", result);
    }

    @PostMapping("/list-by-ids")
    public ApiResponse<List<UserBO>> listByIds(@RequestBody Set<Long> userIds) {
        return ApiResponse.success(userApi.listByIds(userIds));
    }
}
