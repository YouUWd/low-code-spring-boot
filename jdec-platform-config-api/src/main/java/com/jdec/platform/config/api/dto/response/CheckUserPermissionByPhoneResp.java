package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通过手机号检查用户权限响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通过手机号检查用户权限响应")
public class CheckUserPermissionByPhoneResp {

    @Schema(description = "是否有权限")
    private Boolean hasPermission;
}
