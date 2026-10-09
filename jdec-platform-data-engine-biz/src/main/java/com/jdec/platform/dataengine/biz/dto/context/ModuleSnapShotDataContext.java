package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.jooq.DSLContext;

@Data
@Builder
public class ModuleSnapShotDataContext {

    private Long rootModuleId;

    private SysModuleCombineInfo rootModuleInfo;

    private Long dataId;

    private Integer versionNo;

    private NestedModuleDataDTO moduleDataDTO;

    private DSLContext dslContext;

    private Map<Long, SysModuleCombineInfo> moudleCacheMap;
}
