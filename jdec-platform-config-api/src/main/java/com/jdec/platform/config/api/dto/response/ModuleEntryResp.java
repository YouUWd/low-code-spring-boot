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
    private String moduleCode;
    private String moduleName;
    private String description;
    private Boolean enabled;

}
