package com.jdec.platform.dataengine.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleRowNodeDataDTO implements Serializable {

    @Schema(description = "主表，关联表，组合的行记录,需要更新的行记录")
    private Map<String, Object> row;

    private List<ModuleTreeNodeDataDTO> children;
}
