package com.jdec.platform.common_business.biz.controller;

import com.jdec.platform.common_business.api.UserServiceApi;
import com.jdec.platform.common_business.api.dto.response.UserTreeResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/common")
@RequiredArgsConstructor
@Tag(name = "学生管理系统公共接口", description = "提供用户树等共享功能")
public class CommonController {

    private final UserServiceApi userService;

    /**
     * 获取人员架构树
     *
     * @param userType 人员类型和 userName 人员名称
     * @return 人员架构树数据
     * @author ：jdec
     * @description 获取按照部门层级展示的人员架构树，支持按人员类型和名称过滤
     */
    @GetMapping("/get-user-tree")
    @Operation(summary = "获取人员架构树", description = "按照部门层级展示人员架构")
    public ApiResponse<List<UserTreeResp>> getUserTreeList(
            @RequestHeader("Subject-Id") Long subjectId,
            @Schema(description = "用户类型") @RequestParam(defaultValue = "0", required = false)
                    Integer userType,
            @Schema(description = "筛选用户名称") @RequestParam(defaultValue = "", required = false)
                    String userName) {
        return ApiResponse.success(userService.getUserTreeList(subjectId, userType, userName));
    }
}
