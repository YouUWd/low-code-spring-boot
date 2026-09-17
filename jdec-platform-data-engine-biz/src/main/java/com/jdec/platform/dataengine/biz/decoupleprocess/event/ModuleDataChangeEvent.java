package com.jdec.platform.dataengine.biz.decoupleprocess.event;

import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class ModuleDataChangeEvent extends ApplicationEvent {

    private Long actionId;

    public ModuleDataChangeEvent(Object source) {
        super(source);
    }
}
