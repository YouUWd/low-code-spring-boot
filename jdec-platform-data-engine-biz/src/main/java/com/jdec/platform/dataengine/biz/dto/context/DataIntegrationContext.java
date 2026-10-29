package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.BasicModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class DataIntegrationContext extends BasicModel {

    private Long id;

    private Long changeRequestId;

    private Map<Long, List<ModuleFieldDTO>> moduleIdToFieldMap;

    @Schema(description = "模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "55")
    private Long moduleId;

    private Integer version;

    private List<NestedModuleDataDTO> preVersionData;

    private ModuleTreeDTO moduleTree;
}
