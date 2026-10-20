package com.jdec.platform.shared.datasource;

import com.jdec.platform.shared.context.AppContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务级数据源路由：在事务开启、创建物理连接之前，按项目号兜底切换数据源。
 *
 * <p>与现有切面配合关系：
 *
 * <ul>
 *   <li>{@code DynamicProjectDataSourceAspect}（Order=0）、{@code DataSourceAspect}（Order=1）
 *       已经在事务开启前切过数据源时，{@link #doBegin} 检测到 {@code DataSourceContextHolder} 非空，直接沿用，不覆盖；
 *   <li>两者都没覆盖的方法，由本类按 {@code AppContext.projectNo} 兜底；
 *   <li>固定数据源注解（{@code @DataSource}）优先级最高——它在事务前就已经写入 ThreadLocal， {@link #doBegin} 自然沿用。
 * </ul>
 *
 * <p>为什么用 {@link TransactionSynchronizationManager#bindResource} 做标记而不是 ThreadLocal： 事务挂起/恢复（{@code
 * REQUIRES_NEW} 场景）时 resources 会被一起 suspend/resume， 用 ThreadLocal 会在嵌套事务间串状态。
 */
@Slf4j
public class ProjectAwareDataSourceTransactionManager extends DataSourceTransactionManager {

    /** 事务资源标记：本层事务是否由 doBegin 自己切换过数据源。 */
    private static final Object DS_SWITCHED_KEY = new Object();

    private final ProjectProperties projectProperties;

    public ProjectAwareDataSourceTransactionManager(
            DynamicRoutingDataSource dataSource, ProjectProperties projectProperties) {
        super(dataSource);
        this.projectProperties = projectProperties;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        boolean switched = false;
        if (StringUtils.isBlank(DataSourceContextHolder.get())) {
            String projectNo = AppContext.getProjectNo();
            if (StringUtils.isNotBlank(projectNo)) {
                String dsKey = projectProperties.getDataSourceKey(projectNo);
                if (StringUtils.isNotBlank(dsKey)) {
                    DataSourceContextHolder.set(dsKey);
                    switched = true;
                    log.debug(
                            "事务开启前切换数据源至：{}（项目 {}，方法传播级别 {}）",
                            dsKey,
                            projectNo,
                            definition.getPropagationBehavior());
                }
            }
        }

        try {
            super.doBegin(transaction, definition);
        } catch (RuntimeException | Error ex) {
            if (switched) {
                DataSourceContextHolder.clear();
            }
            throw ex;
        }

        if (switched) {
            // doBegin 里 synchronization 必然还未激活；但 bindResource 只操作 resources ThreadLocal，
            // 不依赖 synchronization。REQUIRES_NEW 的 suspend/resume 会连同 DS_SWITCHED_KEY 一起隔离。
            if (TransactionSynchronizationManager.hasResource(DS_SWITCHED_KEY)) {
                // 极端异常场景（外层漏清理），打 WARN 但不覆盖已有标记，防止误 pop
                log.warn("DS_SWITCHED_KEY 已存在，本次事务不重复绑定（当前数据源 {}）", DataSourceContextHolder.get());
            } else {
                TransactionSynchronizationManager.bindResource(DS_SWITCHED_KEY, Boolean.TRUE);
            }
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        try {
            super.doCleanupAfterCompletion(transaction);
        } finally {
            // 只有自己切过的才 pop，避免误清 @DataSource / DynamicProjectDataSourceAspect 的层
            if (TransactionSynchronizationManager.hasResource(DS_SWITCHED_KEY)) {
                try {
                    TransactionSynchronizationManager.unbindResource(DS_SWITCHED_KEY);
                } catch (IllegalStateException ignored) {
                    // Spring 已清理，忽略
                }
                DataSourceContextHolder.clear();
                log.debug("事务结束，恢复数据源至：{}", DataSourceContextHolder.get());
            }
        }
    }
}
