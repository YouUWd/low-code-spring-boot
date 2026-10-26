package com.jdec.platform.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 校验任务执行器：独立线程执行校验，调用线程同步等待并拿到结果。 */
class SubmitCheckTaskExecutorTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final SubmitCheckTaskExecutor taskExecutor = new SubmitCheckTaskExecutor(executor);

    @AfterEach
    void tearDown() {
        AppContext.remove();
        executor.shutdownNow();
    }

    /** 校验线程能拿到调用线程的项目号与主体，否则取不到正确的数据源。 */
    @Test
    void shouldCopyContextToCheckThread() {
        AppContext.setContext("company", 9L);
        AtomicReference<String> projectNo = new AtomicReference<>();
        AtomicReference<Long> subjectId = new AtomicReference<>();

        taskExecutor.runAndWait(
                "上下文校验",
                () -> {
                    projectNo.set(AppContext.getProjectNo());
                    subjectId.set(AppContext.getSubjectId());
                });

        assertEquals("company", projectNo.get());
        assertEquals(9L, subjectId.get());
    }

    /** 校验线程是复用的：执行完必须清掉上下文，不能带到下一次任务。 */
    @Test
    void shouldClearContextAfterCheck() {
        AppContext.setContext("company", 9L);
        AtomicReference<String> projectNoAfterFirstTask = new AtomicReference<>("init");

        taskExecutor.runAndWait("上下文校验", () -> {});
        AppContext.remove();
        taskExecutor.runAndWait(
                "上下文清理校验", () -> projectNoAfterFirstTask.set(AppContext.getProjectNo()));

        assertNull(projectNoAfterFirstTask.get());
    }

    /** 校验不通过：异常要原样抛回调用线程，业务才能中断、事务才能回滚。 */
    @Test
    void shouldRethrowBusinessException() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                taskExecutor.runAndWait(
                                        "异常校验",
                                        () -> {
                                            throw new BusinessException("基本信息 - 统一社会信用代码");
                                        }));

        assertEquals("基本信息 - 统一社会信用代码", exception.getMessage());
    }
}
