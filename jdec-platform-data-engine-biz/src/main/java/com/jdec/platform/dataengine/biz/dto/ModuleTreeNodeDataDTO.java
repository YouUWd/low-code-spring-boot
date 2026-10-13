package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleTreeNodeDataDTO implements Serializable {

    private Long moduleId;

    private List<ModuleRowNodeDataDTO> records;
}
