package com.jdec.platform.common_business.biz.controller;

import com.jdec.platform.common_business.api.DynamicDataAccessServiceApi;
import com.jdec.platform.common_business.biz.dto.RightInfoVO;
import com.jdec.platform.common_business.biz.service.ModuleInfoWithPermissionService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/student/module")
@Tag(
        name = "模块", // 分组名称（文档中显示的分组标签）
        description = "模块" // 分组描述
        )
public class ModuleController {

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    private final DynamicDataAccessServiceApi dynamicDataAccessService;

    @GetMapping("/get/module/apply/field")
    @Operation(summary = "获取模块字段权限", description = "获取模块字段权限")
    public ApiResponse<RightInfoVO> getModuleApplyField(
            @RequestHeader("Subject-Id") Long subjectId,
            @RequestHeader("Role-Id") Long roleId,
            @RequestParam Long moduleId,
            @Schema(description = "当前页面模式(view,edit,create)，如果是创建可以不传使用默认的。")
                    @RequestParam(required = false)
                    String btnType) {
        // Mock-User-Id Subject-Id  Role-Id
        return moduleInfoWithPermissionService.getModuleApplyFieldList(
                subjectId, roleId, moduleId, btnType);
    }

    @GetMapping("/get/menu/apply/field")
    @Operation(summary = "获取模块字段权限", description = "获取模块字段权限")
    public ApiResponse<Map<Object, Object>> getMenuModuleApplyField(
            @RequestHeader("Subject-Id") Long subjectId,
            @RequestHeader("Role-Id") Long roleId,
            @RequestParam Long menuId,
            @Schema(description = "当前页面模式(view,edit,create)，如果是创建可以不传使用默认的。")
                    @RequestParam(required = false)
                    String btnType) {
        // Mock-User-Id Subject-Id  Role-Id
        return dynamicDataAccessService.getModuleApplyFieldListByMenuId(
                subjectId, roleId, menuId, btnType);
    }
}
