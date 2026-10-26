package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysFileApi;
import com.jdec.platform.config.api.dto.response.SysFileUploadResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件上传")
@RestController
@RequestMapping("api/common/files")
@RequiredArgsConstructor
public class SysFileController {

    private final SysFileApi sysFileService;

    @Operation(summary = "上传文件", description = "通用的文件上传接口，可关联所属菜单ID，返回相对路径及文件记录ID")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<SysFileUploadResp> uploadFile(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "关联菜单ID (可选)")
                    @RequestParam(value = "menuId", required = false)
                    Long menuId) {
        return ApiResponse.success(sysFileService.uploadFile(file, menuId));
    }

    @Operation(summary = "批量查询文件记录", description = "根据文件ID列表批量获取文件记录")
    @PostMapping("/list-by-ids")
    public ApiResponse<List<SysFileUploadResp>> listFilesByIds(@RequestBody List<Long> ids) {
        return ApiResponse.success(sysFileService.listFilesByIds(ids));
    }

    @Operation(
            summary = "获取文件路径前缀",
            description = "获取当前环境下的文件访问路径前缀 (Base URL)，前端可与相对路径 (objectKey) 拼接完整访问地址")
    @GetMapping("/path")
    public ApiResponse<String> getPathPrefix() {
        return ApiResponse.success(sysFileService.getPathPrefix());
    }
}
