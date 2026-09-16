package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.biz.dto.BasicModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
public class DetailQueryContext extends BasicModel {

    @Schema(description = "菜单 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "55")
    private Long menuId;

    @Schema(description = "模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "55")
    private Long moduleId;

    @Schema(description = "数据 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "55")
    private Long id;

    private List<Long> relateIds;

    @Schema(description = "版本 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "V2")
    private Integer version;

    @Schema(
            description = "按钮",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "change/view/change-change")
    private String btnType;

    private List<ModuleFieldDTO> fields;
}
