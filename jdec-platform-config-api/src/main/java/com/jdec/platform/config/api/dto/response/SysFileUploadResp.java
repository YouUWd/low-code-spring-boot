package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传响应")
public class SysFileUploadResp {

    @Schema(description = "文件主键ID")
    private Long id;

    @Schema(description = "关联菜单ID")
    private Long menuId;

    @Schema(description = "文件可访问URL")
    private String url;

    @Schema(description = "存储到OSS的相对路径ObjectKey")
    private String objectKey;

    @Schema(description = "存储到OSS的新文件名")
    private String fileName;

    @Schema(description = "原始主文件名 (不含后缀)")
    private String originalName;

    @Schema(description = "文件扩展名")
    private String fileExt;

    @Schema(description = "文件大小(字节)")
    private Long size;
}
