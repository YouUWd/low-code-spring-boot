package com.jdec.platform.common_business.biz.log;

import com.lmax.disruptor.EventHandler;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class LogEventHandler implements EventHandler<LogEvent> {

    private final List<BizLog> bizLogList;

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) throws Exception {

        for (BizLog bizLog : bizLogList) {
            if (bizLog.referToBizLog(event.getBizLogEnum())) {
                bizLog.recordBizLog(event.getBizLogEnum(), event.getTarget());
            }
        }
    }
}
