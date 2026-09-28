package com.jdec.platform.dataengine.biz.decoupleprocess.publisher;

import com.jdec.platform.dataengine.biz.decoupleprocess.event.ModuleDataChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModuleDataSavePublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishModuleDataChangeEvent(ModuleDataChangeEvent changeEvent) {
        applicationEventPublisher.publishEvent(changeEvent);
    }
}
