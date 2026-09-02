package com.jdec.platform.config.biz.audit.listener;

import com.jdec.platform.config.biz.audit.event.SysModuleChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 模块配置变更事件监听器（重构期间暂时屏蔽审计逻辑） */
@Slf4j
@Component
public class SysModuleAuditListener {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handleModuleChangeEvent(SysModuleChangeEvent event) {
        log.debug(
                "模块变更事件监听已暂时屏蔽: eventType={}, moduleId={}",
                event.getEventType(),
                event.getModuleId());
    }
}
