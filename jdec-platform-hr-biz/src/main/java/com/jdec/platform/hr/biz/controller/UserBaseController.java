package com.jdec.platform.hr.biz.controller;

import com.jdec.platform.hr.api.UserBaseApi;
import com.jdec.platform.hr.api.dto.request.CreateUserBaseReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserBaseReq;
import com.jdec.platform.hr.api.dto.response.UserBaseResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户基本信息 REST 控制器 */
@Slf4j
@RestController
@RequestMapping("/api/hr/user-base")
@RequiredArgsConstructor
public class UserBaseController {

    private final UserBaseApi userBaseApi;

    /**
     * 根据用户ID获取用户基本信息
     *
     * @param userId 用户ID
     * @return 用户基本信息
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserBaseResp> getUserBase(@PathVariable Integer userId) {
        Optional<UserBaseResp> userBase = userBaseApi.getUserBaseById(userId);
        return userBase.map(ApiResponse::success).orElseGet(() -> ApiResponse.error(404, "用户不存在"));
    }

    /**
     * 分页查询用户基本信息
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<UserBaseResp>> listUserBase(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize) {
        PageResult<UserBaseResp> result = userBaseApi.getUserBaseByPage(pageNum, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 创建用户基本信息
     *
     * @param request 创建用户基本信息请求
     * @return 用户基本信息
     */
    @PostMapping
    public ApiResponse<UserBaseResp> createUserBase(@RequestBody CreateUserBaseReq request) {
        UserBaseResp result = userBaseApi.createUserBase(request);
        return ApiResponse.success("创建成功", result);
    }

    /**
     * 更新用户基本信息
     *
     * @param request 更新用户基本信息请求
     * @return 用户基本信息
     */
    @PutMapping
    public ApiResponse<UserBaseResp> updateUserBase(@RequestBody UpdateUserBaseReq request) {
        UserBaseResp result = userBaseApi.updateUserBase(request);
        return ApiResponse.success("更新成功", result);
    }

    /**
     * 删除用户基本信息
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUserBase(@PathVariable Integer userId) {
        log.info("删除用户基本信息: {}", userId);
        userBaseApi.deleteUserBase(userId);
        return ApiResponse.success();
    }
}
