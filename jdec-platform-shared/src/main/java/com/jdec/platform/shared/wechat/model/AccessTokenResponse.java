package com.jdec.platform.shared.wechat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 获取access_token响应 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccessTokenResponse extends WeChatWorkResponse {

    /** 访问令牌 */
    @JsonProperty("access_token")
    private String accessToken;

    /** 过期时间（秒） */
    @JsonProperty("expires_in")
    private Integer expiresIn;
}
