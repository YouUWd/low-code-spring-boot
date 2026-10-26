package com.jdec.platform.validation;

import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.RequestContextSnapshot;
import com.jdec.platform.shared.context.RequestContextSnapshotHolder;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 校验任务执行器（异步执行 + 同步等待结果）。
 *
 * <p>校验是穿插在别人的接口（保存、提交、审批）里的，那些接口本身带事务，事务一旦开启连接就锁定在开启事务时所用的库上， 校验再按 {@code company}
 * 等库名去取连接时切不过去，会读到错误的库。这里把校验提交到独立线程执行：
 *
 * <ol>
 *   <li>新线程没有事务绑定，{@code DataSourceResolver} 会按库名返回目标物理库，数据源可以正常切换；
 *   <li>调用线程阻塞等待校验结果，校验不通过时仍把 {@link BusinessException} 原样抛回，业务照常中断、事务照常回滚；
 *   <li>新线程不带调用线程的 ThreadLocal，提交任务时先把项目号、主体、登录用户、MDC 与请求快照复制过去，执行完清理。
 * </ol>
 *
 * <p>注意：独立线程拿到的是另一条连接且不在调用方事务内，只能读到已提交的数据，读不到调用方尚未提交的改动。因此校验应依赖
 * 前端提交的参数或上一笔已落库的数据，不要在“本事务刚写完、还没提交”的数据上做校验。
 */
@Slf4j
@Component
public class SubmitCheckTaskExecutor {

    /** 校验最长等待时间（秒），超时后中断校验线程并提示稍后重试 */
    private static final long TIMEOUT_SECONDS = 60L;

    private final Executor executor;

    public SubmitCheckTaskExecutor(@Qualifier("validationCheckExecutor") Executor executor) {
        this.executor = executor;
    }

    /**
     * 在独立线程执行校验，并阻塞等待结果。
     *
     * @param taskName 校验名称，仅用于日志定位
     * @param task 校验逻辑；校验不通过时按业务约定抛出 {@link BusinessException}
     * @throws BusinessException 校验不通过、执行超时或被中断
     */
    public void runAndWait(String taskName, Runnable task) {
        if (task == null) {
            return;
        }
        // 在调用线程取值：新线程不带调用方 ThreadLocal
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        LoginUser loginUser = UserContext.getLoginUser();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        RequestContextSnapshot requestSnapshot = RequestContextSnapshotHolder.get();

        FutureTask<Void> future;
        try {
            future =
                    new FutureTask<>(
                            () -> {
                                applyContext(
                                        projectNo,
                                        subjectId,
                                        loginUser,
                                        mdcContext,
                                        requestSnapshot);
                                try {
                                    task.run();
                                } finally {
                                    clearContext();
                                }
                            },
                            null);
            executor.execute(future);
        } catch (RejectedExecutionException e) {
            log.error("校验任务[{}]提交失败：校验线程池已满", taskName, e);
            throw new BusinessException("系统繁忙，请稍后重试", e);
        }

        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.error("校验任务[{}]等待结果时被中断", taskName, e);
            throw new BusinessException("校验被中断，请稍后重试", e);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("校验任务[{}]执行超时（{}秒）", taskName, TIMEOUT_SECONDS, e);
            throw new BusinessException("校验超时，请稍后重试", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            log.error("校验任务[{}]执行失败", taskName, cause);
            throw new BusinessException("校验执行失败", cause);
        }
    }

    /** 把调用线程的上下文复制到校验线程。 */
    private static void applyContext(
            String projectNo,
            Long subjectId,
            LoginUser loginUser,
            Map<String, String> mdcContext,
            RequestContextSnapshot requestSnapshot) {
        AppContext.setContext(projectNo, subjectId);
        if (loginUser != null) {
            UserContext.setLoginUser(loginUser);
        }
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
        if (requestSnapshot != null) {
            RequestContextSnapshotHolder.set(requestSnapshot);
        }
    }

    /** 校验线程由线程池复用，执行完必须清理上下文。 */
    private static void clearContext() {
        AppContext.remove();
        UserContext.remove();
        RequestContextSnapshotHolder.clear();
        MDC.clear();
    }
}
