package com.jdec.platform.dataengine.biz.decoupleprocess.event;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.BasicSaveModel;
import com.jdec.platform.shared.context.RequestContextSnapshot;
import java.util.Map;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class ModuleDataSaveChangeLogEvent extends ApplicationEvent {
    public ModuleDataSaveChangeLogEvent(Object source) {
        super(source);
    }

    private BasicSaveModel saveModel;

    private Boolean isApproval;

    private Map<Long, SysModuleCombineInfo> moduleCacheMap;

    private Long structureRootModuleId;

    private Long rootDataId;

    private NestedModuleDataDTO originalData;

    private NestedModuleDataDTO changedData;

    private RequestContextSnapshot requestContextSnapshot;
}
