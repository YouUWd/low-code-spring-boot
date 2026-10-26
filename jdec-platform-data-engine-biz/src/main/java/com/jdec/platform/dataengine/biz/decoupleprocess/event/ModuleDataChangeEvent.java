package com.jdec.platform.dataengine.biz.decoupleprocess.event;

import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class ModuleDataChangeEvent extends ApplicationEvent {

    private ModuleDataPostContext moduleDataPostContext;

    public ModuleDataChangeEvent(Object source) {
        super(source);
    }
}
