package com.jdec.platform.dataengine.biz.operatelog.aspect;

import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicSaveResp;
import com.jdec.platform.dataengine.biz.operatelog.annotation.SaveOperateLog;
import com.jdec.platform.dataengine.biz.operatelog.service.SaveOperateLogContext;
import com.jdec.platform.dataengine.biz.operatelog.service.SaveOperateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 保存操作日志切面：拦截带 {@link SaveOperateLog} 的保存方法。
 *
 * <p>保存前取旧数据（{@code prepare}），保存成功后对比生成并写日志（{@code finish}）。日志与业务无事务关系， 两个阶段均做异常隔离，任何失败都不影响业务保存。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SaveOperateLogAspect {

    private final SaveOperateLogService saveOperateLogService;

    @Around("@annotation(saveOperateLog)")
    public Object around(ProceedingJoinPoint pjp, SaveOperateLog saveOperateLog) throws Throwable {
        DynamicSaveReq req = extractRequest(pjp);
        SaveOperateLogContext context = null;
        if (saveOperateLog.enabled() && req != null) {
            try {
                context = saveOperateLogService.prepare(req);
            } catch (Exception e) {
                log.warn("保存操作日志：保存前预处理失败，跳过日志记录", e);
            }
        }

        Object result = pjp.proceed();

        if (context != null && result instanceof DynamicSaveResp resp) {
            try {
                saveOperateLogService.finish(context, req, resp);
            } catch (Exception e) {
                log.error("保存操作日志：生成日志失败", e);
            }
        }
        return result;
    }

    private DynamicSaveReq extractRequest(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof DynamicSaveReq req) {
                return req;
            }
        }
        return null;
    }
}
