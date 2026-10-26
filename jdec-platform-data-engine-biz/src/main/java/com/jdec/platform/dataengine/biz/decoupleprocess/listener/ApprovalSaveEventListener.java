package com.jdec.platform.dataengine.biz.decoupleprocess.listener;

import com.jdec.platform.dataengine.biz.decoupleprocess.event.ApprovalProcessEvent;
import com.jdec.platform.dataengine.biz.decoupleprocess.event.ApprovalSaveEvent;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApprovalSaveEventListener {

    @Async
    @EventListener(ApprovalProcessEvent.class)
    public void handleApprovalProcessEvent(ApprovalSaveEvent event) {
        log.info("Received approval save event: {}", event);
        try {
            AppContext.setContext(
                    event.getBasicSaveModel().getProjectNo(),
                    event.getBasicSaveModel().getSubjectId());
            UserContext.setLoginUser(event.getBasicSaveModel().getLoginUser());
        } catch (Exception e) {

        } finally {
            AppContext.remove();
            UserContext.remove();
        }
    }
}
