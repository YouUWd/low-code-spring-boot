package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/** 模块表字段树响应 */
@Data
@Schema(description = "模块表字段树节点")
public class SysModuleFieldTreeResp implements Serializable {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "模块下的表和字段列表")
    private List<ModuleTableTreeResp> tables;
}
