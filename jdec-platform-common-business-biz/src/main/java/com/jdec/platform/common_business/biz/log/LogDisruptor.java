package com.jdec.platform.common_business.biz.log;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
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
public class LogDisruptor {

    private final ExecutorService threadPool =
            new ThreadPoolExecutor(
                    2,
                    4,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(10),
                    new ThreadFactoryBuilder().setNameFormat("LogDisruptor-%d").build(),
                    new ThreadPoolExecutor.CallerRunsPolicy());

    private final Disruptor<LogEvent> logEventDisruptor =
            new Disruptor<>(new LogEventFactory(), 1024, threadPool);

    private final LogEventHandler logEventHandler;

    @PostConstruct
    public void start() {
        logEventDisruptor.handleEventsWith(logEventHandler);
        logEventDisruptor.start();
    }

    @PreDestroy
    public void stop() {
        logEventDisruptor.shutdown();
    }

    public void publishLogEvent(BizLogEnum logEnum, Object target) {
        logEventDisruptor.publishEvent(
                (x, y) -> {
                    x.setBizLogEnum(logEnum);
                    x.setTarget(target);
                });
    }
}
