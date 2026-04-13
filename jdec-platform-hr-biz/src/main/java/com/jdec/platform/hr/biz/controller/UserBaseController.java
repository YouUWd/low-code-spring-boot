package com.jdec.platform.hr.biz.controller;

import com.jdec.platform.hr.api.dto.response.UserBaseResp;
import com.jdec.platform.hr.biz.service.UserBaseService;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户基本信息 REST 控制器 */
@RestController
@RequestMapping("/api/hr/user-base")
@RequiredArgsConstructor
public class UserBaseController {

    private final UserBaseService userBaseService;

    /**
     * 根据用户ID获取用户基本信息
     *
     * @param userId 用户ID
     * @return 用户基本信息
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserBaseResp> getUserBase(@PathVariable Integer userId) {
        Optional<UserBaseResp> userBase = userBaseService.getUserBaseById(userId);
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
    public ApiResponse<PageResult<UserBaseResp>> getUserBaseByPage(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize) {
        PageResult<UserBaseResp> result = userBaseService.getUserBaseByPage(pageNum, pageSize);
        return ApiResponse.success(result);
    }
}
