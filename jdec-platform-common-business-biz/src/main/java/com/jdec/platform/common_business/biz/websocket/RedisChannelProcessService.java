package com.jdec.platform.common_business.biz.websocket;

import java.util.List;
import java.util.Set;

public interface RedisChannelProcessService {

    Set<Long> findFinalNotifyModule(Long subjectId, List<Long> moduleId, Long roleId);
}
