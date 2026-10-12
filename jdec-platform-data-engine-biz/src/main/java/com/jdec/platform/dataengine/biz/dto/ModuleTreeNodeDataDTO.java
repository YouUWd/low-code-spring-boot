package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class ModuleTreeNodeDataDTO implements Serializable {

    private Long moduleId;

    private List<ModuleRowNodeDataDTO> records;
}
