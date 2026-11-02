package com.jdec.platform.common.biz.listener.changeform;

import com.jdec.platform.dataengine.api.event.ChangeApplyEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeApplyEventListener {

    private final List<com.jdec.platform.common.biz.listener.changeform.ChangeReqFormEventProcessor>
            changeReqFormProcessors;

    @Async
    @EventListener(ChangeApplyEvent.class)
    public void handleEvent(ChangeApplyEvent event) {
        log.info("Received change apply event: {}", event);
        changeReqFormProcessors.forEach(
                processor -> {
                    if (processor.isSupport(event)) {
                        processor.processChangeRequestEvent(event);
                    }
                });
    }
}
