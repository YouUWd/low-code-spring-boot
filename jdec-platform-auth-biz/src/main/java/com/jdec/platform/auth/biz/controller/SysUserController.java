package com.jdec.platform.auth.biz.controller;

import com.jdec.platform.auth.api.SysUserApi;
import com.jdec.platform.auth.api.dto.response.UserEntryResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户控制器
 *
 * <p>处理用户相关的 HTTP 请求
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserApi userService;

    /**
     * 分页获取用户列表
     *
     * @param pageNum 页码，默认为 1
     * @param pageSize 每页大小，默认为 10
     * @return 分页用户信息
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<UserEntryResp>> listUsers(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize) {
        PageResult<UserEntryResp> result = userService.listUsers(pageNum, pageSize);
        return ApiResponse.success(result);
    }
}
