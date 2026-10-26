package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import com.jdec.platform.dataengine.biz.dto.BasicSaveModel;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.shared.context.RequestContextSnapshot;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.Map;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ModuleDataPostContext extends BasicSaveModel {

    private Long actionId;

    private NestedModuleDataDTO nestedModuleDataDTO;

    private Map<Long, Map<Long, RowChangeDTO>> changeMap;

    private Map<Long, SysModuleCombineInfo> moduleCacheMap;

    private RequestContextSnapshot requestContextSnapshot;

    private LoginUser loginUser;
}
