package com.jdec.platform.common_business.biz.log;

import com.lmax.disruptor.EventHandler;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class LogMsgEventHandler implements EventHandler<LogMsgEvent> {

    private final List<BizLog> bizLogList;

    private final List<BizMsg> bizMsgList;

    @Override
    public void onEvent(LogMsgEvent event, long sequence, boolean endOfBatch) throws Exception {

        if (event.getBizLogEnum() != null) {
            for (BizLog bizLog : bizLogList) {
                if (bizLog.referToBizLog(event.getBizLogEnum())) {
                    bizLog.processBizLog(event.getBizLogEnum(), event.getTarget());
                }
            }
        }

        if (event.getBizMsgEnum() != null) {
            for (BizMsg bizMsg : bizMsgList) {
                if (bizMsg.referToBizMsg(event.getBizMsgEnum())) {
                    bizMsg.processBizMsg(event.getBizMsgEnum(), event.getTarget());
                }
            }
        }
    }
}
