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
    private String name;
    private String description;
    private Integer isEnabled;

    public boolean isValidCode() {
        return code != null && !code.trim().isEmpty();
    }

    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }
}
