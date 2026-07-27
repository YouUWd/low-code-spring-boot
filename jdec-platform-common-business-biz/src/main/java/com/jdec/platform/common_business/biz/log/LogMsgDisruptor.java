package com.jdec.platform.common_business.biz.log;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.enums.BizMsgEnum;
import com.lmax.disruptor.dsl.Disruptor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class LogMsgDisruptor {

    private final ExecutorService threadPool =
            new ThreadPoolExecutor(
                    2,
                    4,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(10),
                    new ThreadFactoryBuilder().setNameFormat("LogDisruptor-%d").build(),
                    new ThreadPoolExecutor.CallerRunsPolicy());

    private final Disruptor<LogMsgEvent> logMsgEventDisruptor =
            new Disruptor<>(new LogMsgEventFactory(), 1024, threadPool);

    private final LogMsgEventHandler logEventHandler;

    @PostConstruct
    public void start() {
        logMsgEventDisruptor.handleEventsWith(logEventHandler);
        logMsgEventDisruptor.start();
    }

    @PreDestroy
    public void stop() {
        logMsgEventDisruptor.shutdown();
    }

    public void publishLogEvent(BizLogEnum logEnum, Object target) {
        logMsgEventDisruptor.publishEvent(
                (x, y) -> {
                    x.setBizLogEnum(logEnum);
                    x.setBizMsgEnum(null);
                    x.setTarget(target);
                });
    }

    public void publishMsgEvent(BizMsgEnum msgEnum, Object target) {
        logMsgEventDisruptor.publishEvent(
                (x, y) -> {
                    x.setBizMsgEnum(msgEnum);
                    x.setBizLogEnum(null);
                    x.setTarget(target);
                });
    }
}
