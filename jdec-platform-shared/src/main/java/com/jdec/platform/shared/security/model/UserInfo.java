package com.jdec.platform.shared.security.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo implements Serializable {
    private Long id;
    private Long userId;
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码（加密）")
    private String password;

    @Schema(description = "状态 0-禁用 1-启用")
    private Integer status;

    @Schema(description = "应用编码")
    private String appCode;
}
