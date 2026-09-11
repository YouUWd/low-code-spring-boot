package com.jdec.platform.dataengine.biz.decoupleprocess.event;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import java.util.Map;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class ModuleDataChangeEvent extends ApplicationEvent {

    private RecordSaveContext recordSaveContext;

    private Map<String, Object> before;

    private Map<String, Object> after;

    public ModuleDataChangeEvent(Object source) {
        super(source);
    }
}
