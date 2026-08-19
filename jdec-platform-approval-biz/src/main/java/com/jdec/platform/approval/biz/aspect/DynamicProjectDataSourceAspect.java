package com.jdec.platform.approval.biz.aspect;

import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceContextHolder;
import com.jdec.platform.shared.datasource.ProjectProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(0) // 优先于 @DataSource 切面（其 Order=1）
@RequiredArgsConstructor
public class DynamicProjectDataSourceAspect {

    private final ProjectProperties projectProperties;

    @Around(
            "execution(* com.jdec.platform.approval.biz.service.impl.ApprovalEngineImpl.*(..))") // 按需调整包路径
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String projectNo = AppContext.getProjectNo();
        boolean switched = false;

        if (projectNo != null && !projectNo.isBlank()) {
            String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
            if (dataSourceKey != null && !dataSourceKey.isBlank()) {
                DataSourceContextHolder.set(dataSourceKey);
                switched = true;
                log.debug("AOP 切换数据源至：{} (项目：{})", dataSourceKey, projectNo);
            }
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (switched) {
                DataSourceContextHolder.clear(); // 弹出自己压入的
            }
        }
    }
}
