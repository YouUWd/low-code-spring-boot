package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 业务系统数据权限响应 */
@Data
@Schema(description = "业务系统数据权限响应")
public class BizDataPermissionResp {

    @Schema(description = "数据权限编码", example = "student*id")
    private String dataPermissionCode;

    @Schema(description = "主体ID", example = "3")
    private String subject;

    @Schema(description = "业务数据列表")
    private List<String> bizNames;
}
