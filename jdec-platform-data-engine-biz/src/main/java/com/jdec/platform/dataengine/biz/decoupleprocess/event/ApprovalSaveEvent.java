package com.jdec.platform.dataengine.biz.decoupleprocess.event;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.BasicSaveModel;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class ApprovalSaveEvent extends ApplicationEvent {

    private BasicSaveModel basicSaveModel;

    private NestedModuleDataDTO detailData;

    private Long rootModuleId;

    private Long rootModuleDataId;

    private String businessNo;

    private Integer versionNo;

    public ApprovalSaveEvent(Object source) {
        super(source);
    }
}
