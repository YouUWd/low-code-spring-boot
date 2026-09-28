package com.jdec.platform.dataengine.biz.decoupleprocess.publisher;

import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.dataengine.biz.decoupleprocess.event.ApprovalProcessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalProcessPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishApprovalProcess(ApprovalProcessReq approvalProcessReq) {
        applicationEventPublisher.publishEvent(new ApprovalProcessEvent(null));
    }
}
