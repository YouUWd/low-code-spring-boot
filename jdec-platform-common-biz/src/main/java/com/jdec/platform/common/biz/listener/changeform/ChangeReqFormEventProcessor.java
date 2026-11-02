package com.jdec.platform.common.biz.listener.changeform;

import com.jdec.platform.dataengine.api.event.ChangeApplyEvent;

public interface ChangeReqFormEventProcessor {

    Boolean isSupport(ChangeApplyEvent event);

    void processChangeRequestEvent(ChangeApplyEvent event);
}
