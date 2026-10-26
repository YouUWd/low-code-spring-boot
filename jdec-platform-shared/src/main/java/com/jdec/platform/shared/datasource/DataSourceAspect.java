package com.jdec.platform.shared.datasource;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据源切换 AOP 切面。
 *
 * <p>拦截标注了 {@link DataSource} 注解的类或方法，在执行前切换数据源， 执行后还原，确保不污染后续操作。
 *
 * <p>{@code @Order(1)} 确保在事务切面（默认 {@code @Order(Ordered.LOWEST_PRECEDENCE)}）
 * <b>之前</b>执行，否则事务已获取连接后切换数据源无效。
 */
@Slf4j
@Aspect
@Order(1)
@Component
public class DataSourceAspect {

    @Pointcut(
            "@annotation(com.jdec.platform.shared.datasource.DataSource) "
                    + "|| @within(com.jdec.platform.shared.datasource.DataSource)")
    public void dataSourcePointcut() {}

    @Around("dataSourcePointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 方法级注解优先
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        DataSource ds = method.getAnnotation(DataSource.class);
        if (ds == null) {
            // 回退到类级注解
            ds = point.getTarget().getClass().getAnnotation(DataSource.class);
        }

        if (ds != null) {
            DataSourceContextHolder.set(ds.value());
        }

        try {
            return point.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
