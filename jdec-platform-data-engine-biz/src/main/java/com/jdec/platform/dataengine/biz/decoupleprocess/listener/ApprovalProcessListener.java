package com.jdec.platform.dataengine.biz.decoupleprocess.listener;

import com.jdec.platform.dataengine.biz.decoupleprocess.event.ApprovalProcessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApprovalProcessListener {

    @Async
    @EventListener(ApprovalProcessEvent.class)
    public void handleApprovalProcessEvent(ApprovalProcessEvent approvalProcessEvent) {}
}
