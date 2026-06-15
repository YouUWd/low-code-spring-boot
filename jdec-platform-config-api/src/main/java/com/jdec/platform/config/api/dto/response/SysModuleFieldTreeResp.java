package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.enums.ModuleTypeEnum;
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

    @Schema(description = "模块类型")
    private ModuleTypeEnum moduleType;

    @Schema(description = "数据来源主体ID集合")
    private String sourceSubjects;

    @Schema(description = "是否模块业务定义")
    private Integer bizDefFlag;

    @Schema(description = "模块下的表和字段列表")
    private List<ModuleTableTreeResp> tables;

    @Schema(description = "子模块列表")
    private List<SysModuleFieldTreeResp> children;
}
