package com.jdec.platform.shared.wechat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 企业微信API通用响应 */
@Data
public class WeChatWorkResponse {

    /** 错误码 */
    @JsonProperty("errcode")
    private Integer errCode;

    /** 错误信息 */
    @JsonProperty("errmsg")
    private String errMsg;

    /** 判断请求是否成功 */
    public boolean isSuccess() {
        return errCode != null && errCode == 0;
    }
}
