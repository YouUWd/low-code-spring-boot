package com.jdec.platform.common_business.biz.auth.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SysInfoDTO {

    private String title;

    private String logo;

    private String icon;

    @JsonProperty("is_subject")
    private Integer isSubject;

    @JsonProperty("subject_color")
    private String subjectColor;

    @JsonProperty("login_bg_img")
    private String loginBgImg;

    @JsonProperty("internal_url")
    private String internalUrl;

    @JsonProperty("user_url")
    private String userUrl;

    @JsonProperty("formal_url")
    private String formalUrl;
}
