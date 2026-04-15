package com.jdec.platform.config.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色信息响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntryResp {
    private Long id;
    private String code;
    private String roleName;
    private String description;
    private Boolean enabled;

}
