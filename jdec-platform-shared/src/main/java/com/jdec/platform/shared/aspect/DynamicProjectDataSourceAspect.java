package com.jdec.platform.shared.aspect;

import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceContextHolder;
import com.jdec.platform.shared.datasource.ProjectProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Aspect
@Component
@Order(0) // 优先于 @DataSource 切面（其 Order=1）
@RequiredArgsConstructor
public class DynamicProjectDataSourceAspect {

    private final ProjectProperties projectProperties;

    @Around(
            "execution(* com.jdec.platform.common.biz.service.impl.CommonApprovalServiceImpl.businessSave(..))"
                    + " || execution(* com.jdec.platform.approval.biz.service.impl.ApprovalEngineImpl.*(..))") // 按需调整包路径
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug(
                "syncActive={}, actualTxActive={}, txName={}",
                TransactionSynchronizationManager.isSynchronizationActive(),
                TransactionSynchronizationManager.isActualTransactionActive(),
                TransactionSynchronizationManager.getCurrentTransactionName());

        Map<Object, Object> rm = TransactionSynchronizationManager.getResourceMap();
        rm.forEach(
                (k, v) ->
                        log.debug(
                                "resource key={} [{}], value={} [{}]",
                                k,
                                k == null ? "null" : k.getClass().getName(),
                                v,
                                v == null ? "null" : v.getClass().getName()));
        // ===== 核心判断：事务已绑定数据源则完全不切换 =====
        if (isTransactionResourceBound()) {
            log.debug("检测到活跃事务已绑定数据源连接，跳过项目数据源切换，沿用当前事务连接");
            return joinPoint.proceed();
        }

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

    /** 判断当前线程是否已经存在活跃事务并绑定了 DataSource 连接。 */
    private boolean isTransactionResourceBound() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return false;
        }
        for (Object value : TransactionSynchronizationManager.getResourceMap().values()) {
            if (value instanceof ConnectionHolder) {
                return true;
            }
        }
        return false;
    }
}
