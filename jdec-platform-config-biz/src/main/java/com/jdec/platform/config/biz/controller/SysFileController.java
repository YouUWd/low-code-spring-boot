package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysFileApi;
import com.jdec.platform.config.api.dto.response.SysFileUploadResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件上传")
@RestController
@RequestMapping("api/common/files")
@RequiredArgsConstructor
public class SysFileController {

    private final SysFileApi sysFileService;

    @Operation(summary = "上传文件", description = "通用的文件上传接口，返回可访问的完整URL")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<SysFileUploadResp> uploadFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(sysFileService.uploadFile(file));
    }
}
