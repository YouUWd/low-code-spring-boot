package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysProjectSubjectApi;
import com.jdec.platform.config.api.dto.response.SysProjectSubjectResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 项目主体关联 REST 控制器
 *
 * <p>提供项目主体的分页查询、列表查询、详情查询、新增编辑、删除接口。
 */
@RestController
@RequestMapping("/api/config/project-subject")
@RequiredArgsConstructor
@Tag(name = "项目主体关联", description = "项目主体的分页查询、列表查询、详情查询、新增编辑、删除接口")
public class SysProjectSubjectController {

    private final SysProjectSubjectApi sysProjectSubjectApi;

    @GetMapping("/list")
    @Operation(summary = "查询所有项目主体（不分页）")
    public ApiResponse<List<SysProjectSubjectResp>> list() {
        return ApiResponse.success(sysProjectSubjectApi.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询项目主体")
    public ApiResponse<SysProjectSubjectResp> getById(
            @Parameter(description = "项目主体ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysProjectSubjectApi.getById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除项目主体")
    public ApiResponse<Void> delete(
            @Parameter(description = "项目主体ID", example = "1") @PathVariable Long id) {
        sysProjectSubjectApi.deleteProjectSubject(id);
        return ApiResponse.success();
    }
}
