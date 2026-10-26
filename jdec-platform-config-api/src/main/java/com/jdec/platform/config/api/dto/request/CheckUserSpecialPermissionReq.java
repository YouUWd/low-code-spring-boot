package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 检查用户特殊权限请求 */
@Data
@Schema(description = "检查用户特殊权限请求")
public class CheckUserSpecialPermissionReq {

    @Schema(description = "用户ID", example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "特殊权限编码", example = "simulate_login")
    @NotBlank(message = "特殊权限编码不能为空")
    private String rightCode;
}
