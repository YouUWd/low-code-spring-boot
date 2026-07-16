package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户树请求参数对象")
public class UserTreeReq {
    @Schema(description = "用户类别")
    private Integer userType;

    @Schema(description = "用户名称")
    private String userName;
}
