package com.jdec.platform.config.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块信息响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleEntryResp {
    private Long id;
    private String code;
    private String name;
    private String description;

    public boolean isValidCode() {
        return code != null && !code.trim().isEmpty();
    }

    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }
}
