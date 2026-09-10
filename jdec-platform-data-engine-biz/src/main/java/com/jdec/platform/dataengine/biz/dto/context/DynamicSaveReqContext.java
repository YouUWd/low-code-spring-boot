package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.TabTreeNodeDTO;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DynamicSaveReqContext implements Serializable {

    // 当前模块的请求数据
    private NestedModuleDataDTO nestedModuleData;

    // 父模块ID
    private Long parentModuleId;

    // 父模块主表ID
    private Long parentModulePrimaryTableId;

    private TabTreeNodeDTO tabTreeNode;
}
