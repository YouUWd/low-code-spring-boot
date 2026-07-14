package com.jdec.platform.common_business.biz.auth.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户登录后信息返回数据")
public class LoginResult {
    private String token;

    @Schema(description = "用户相关信息")
    private UserInfo userInfo;
}
