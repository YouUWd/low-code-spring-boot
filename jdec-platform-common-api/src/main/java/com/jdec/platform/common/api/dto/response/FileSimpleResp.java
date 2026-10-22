package com.jdec.platform.common.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文件/图片简要信息响应 (用于字段业务类型为 image、attachment 等场景的对象转换) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "文件/图片简要信息响应")
public class FileSimpleResp implements Serializable {

    @Schema(description = "文件ID (sys_file 主键ID)", example = "1001")
    private Long id;

    @Schema(description = "文件名称 / 显示名 (对应 originalName)", example = "company_logo")
    private String name;

    @Schema(description = "文件扩展名", example = "png")
    private String fileExt;

    @Schema(
            description = "文件访问 URL",
            example =
                    "http://192.168.60.8:9001/jdec-platform/2026/10/20/47cc4aa4b501464183aff5693567104d.png")
    private String url;
}
