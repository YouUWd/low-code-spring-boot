package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 模块简易树形响应 DTO */
@Data
@Schema(description = "模块简易树形节点")
public class SysModuleSimpleTreeResp {

    @Schema(description = "模块ID", example = "1")
    private Long id;

    @Schema(description = "模块编码", example = "sys_user")
    private String moduleCode;

    @Schema(description = "模块名称", example = "用户管理")
    private String moduleName;

    @Schema(description = "子模块列表")
    private List<SysModuleSimpleTreeResp> children;
}
