package com.jdec.platform.dataengine.biz.decoupleprocess.listener;

import com.jdec.platform.dataengine.biz.decoupleprocess.event.ModuleDataChangeEvent;
import com.jdec.platform.dataengine.biz.service.IModuleDataFieldChangeContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModuleDataChangeProcessLogListener {

    private final IModuleDataFieldChangeContentService iModuleDataFieldChangeContentService;

    @Async
    @EventListener(ModuleDataChangeEvent.class)
    public void handleApprovalProcessEvent(ModuleDataChangeEvent changeEvent) {
        if (changeEvent != null && changeEvent.getActionId() != null) {
            log.info("处理数据变更日志，actionId：{}", changeEvent.getActionId());
        }
    }
}
