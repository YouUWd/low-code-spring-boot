package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.config.api.dto.response.SysModuleFieldTreeResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 模块字段管理控制器 提供获取包含物理表、复合表及其字段的纯净树形展示端点 */
@Slf4j
@Tag(name = "模块字段权限管理", description = "提供模块、表和字段展示 the 树形关系端点")
@RestController
@RequestMapping("/api/config/sys/module-field-permission")
@RequiredArgsConstructor
public class SysModuleFieldPermissionController {

    private final SysModuleFieldPermissionApi sysModuleFieldPermissionService;

    /**
     * 获取纯净的模块表字段树
     *
     * <p>项目上下文通过 X-Project-No 请求头指定，主体上下文通过 X-Subject-Id 指定。
     *
     * @return 包含物理表、复合表以及其下字段的纯净树形结构
     */
    @Operation(summary = "获取纯净模块字段树", description = "高性能全批量装配输出模块-表（简单物理表与复合表）-字段树")
    @GetMapping("/tree")
    public ApiResponse<List<SysModuleFieldTreeResp>> getModuleTableFieldTree(
            @RequestParam(required = false) Integer category) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("获取项目 {} 主体 {} 下的纯净模块表字段树, category={}", projectNo, subjectId, category);
        List<SysModuleFieldTreeResp> result =
                sysModuleFieldPermissionService.getModuleTableFieldTree(
                        projectNo, subjectId, category);
        return ApiResponse.success(result);
    }

    /**
     * 查询指定角色在某模块下按表及权限类型分组的字段集合
     *
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @return 按表及权限类型分类 of 字段集合的响应
     */
    @Operation(summary = "查询指定角色和模块的字段权限分组集合", description = "返回模块下各个表关联的：可读字段集合、可写字段集合、可更新字段集合")
    @GetMapping("/role-permissions")
    public ApiResponse<List<PermissionTableGroupResp>> getRoleModuleFieldPermissions(
            @RequestParam Long roleId, @RequestParam Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 主体 {} 下角色 {} 在模块 {} 的字段权限表级分组集合", projectNo, subjectId, roleId, moduleId);
        List<PermissionTableGroupResp> result =
                sysModuleFieldPermissionService.getRoleModuleFieldPermissions(
                        projectNo, subjectId, roleId, moduleId);
        return ApiResponse.success(result);
    }
}
