package com.jdec.platform.shared.validation;

import com.jdec.platform.shared.dto.BaseRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 请求验证拦截器
 *
 * <p>自动验证所有 BaseRequest 对象
 */
@Aspect
@Component
@Slf4j
public class RequestValidationInterceptor {

    /**
     * 拦截所有 Controller 方法
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("execution(* com.jdec.platform.*.biz.controller.*.*(..))")
    public Object validateRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        // 遍历方法参数，验证 BaseRequest 对象
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof BaseRequest) {
                BaseRequest request = (BaseRequest) arg;
                log.debug("验证请求对象: {}", request.getClass().getSimpleName());
                request.validate();
            }
        }

        // 执行原方法
        return joinPoint.proceed();
    }
}
