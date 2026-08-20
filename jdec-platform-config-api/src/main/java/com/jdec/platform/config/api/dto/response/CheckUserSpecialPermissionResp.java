package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 检查用户特殊权限响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "检查用户特殊权限响应")
public class CheckUserSpecialPermissionResp {

    @Schema(description = "是否拥有权限", example = "true")
    private Boolean hasPermission;
}
