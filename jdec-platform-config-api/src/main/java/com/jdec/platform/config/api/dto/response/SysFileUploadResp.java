package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件上传响应")
public class SysFileUploadResp {

    @Schema(description = "文件可访问URL")
    private String url;

    @Schema(description = "存储到OSS的新文件名")
    private String fileName;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "文件大小(字节)")
    private Long size;
}
