package com.jdec.platform.common_business.biz.dto.configcenter.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块树模型数据")
public class ModuleNodeResp {

    @Schema(description = "模块ID")
    private Long id;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "子模块数据")
    private List<ModuleNodeResp> children;
}
