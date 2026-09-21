package com.jdec.platform.auth.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserCheckResp {

    @JsonProperty("has_permission")
    private Boolean hasPermission;

    @JsonProperty("sys_info")
    private SysInfoDTO sysInfo;
}
