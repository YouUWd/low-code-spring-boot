package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.biz.service.CommonApprovalService;
import com.jdec.platform.common.biz.service.UserService;
import com.jdec.platform.config.api.dto.response.UserSearchTreeNodeResp;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 平台通用公共接口。
 *
 * <p>提供组织架构树等平台级共享功能。
 */
@Slf4j
@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
@Tag(name = "通用公共接口", description = "提供组织架构树等平台级共享功能")
public class CommonController {

    private final UserService userService;
    private final DataEngineApi dataEngineApi;
    private final CommonApprovalService commonApprovalService;

    /**
     * 获取人员架构树
     *
     * @param userType 人员类型和 userName 人员名称
     * @return 人员架构树数据
     * @description 获取按照部门层级展示的人员架构树，支持按人员类型和名称过滤
     */
    @GetMapping("/user-tree")
    @Operation(summary = "获取人员架构树", description = "按照部门层级展示人员架构")
    public ApiResponse<List<UserSearchTreeNodeResp>> getUserTreeList(
            @Schema(description = "用户类型") @RequestParam(defaultValue = "0", required = false)
                    Integer userType,
            @Schema(description = "筛选用户名称") @RequestParam(defaultValue = "", required = false)
                    String userName) {
        return ApiResponse.success(userService.getUserTreeList(userType, userName));
    }

    @PostMapping("/business/save")
    @Operation(summary = "保存数据", description = "保存数据")
    public ApiResponse<Long> businessSave(@RequestBody DynamicSaveReq req) {
        try {
            return ApiResponse.success(commonApprovalService.businessSave(req));
        } catch (Exception ex) {
            log.error(
                    "Common DataEngine save failed: moduleId={},err={}",
                    req.getNestedModuleData().getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/business/submit")
    @Operation(summary = "提交数据", description = "提交数据")
    public ApiResponse<Long> businessSubmit(@RequestBody DynamicSaveReq req) {
        try {
            return ApiResponse.success(commonApprovalService.businessSubmit(req));
        } catch (Exception ex) {
            log.error(
                    "Common DataEngine save failed: moduleId={},err={}",
                    req.getNestedModuleData().getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/detailHead")
    @Operation(summary = "查询详情头部信息", description = "查询详情头部信息")
    public ApiResponse<Map<String, Object>> getDetailHead(
            @Schema(description = "菜单ID") @RequestParam Long menuId,
            @Schema(description = "业务ID") @RequestParam Long recordId,
            @Schema(description = "查看类型") @RequestParam(defaultValue = "", required = false)
                    String btnType) {
        try {
            return ApiResponse.success(
                    commonApprovalService.getDetailHead(menuId, recordId, btnType));
        } catch (Exception ex) {
            log.error(
                    "Common DataEngine save failed: menuId={},err={}", menuId, ex.getMessage(), ex);
            throw ex;
        }
    }
}
