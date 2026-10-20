package com.jdec.platform.shared.datasource;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;

/**
 * jOOQ DSLContext 工厂。
 *
 * <p>关键设计：所有 DSLContext 共用一个 {@link TransactionAwareDataSourceProxy}， 其 target 就是事务管理器绑定的 {@link
 * DynamicRoutingDataSource}。
 *
 * <p>连接获取链路：
 *
 * <pre>
 *   jOOQ 执行 SQL
 *     → DataSourceConnectionProvider.acquire()
 *       → TransactionAwareDataSourceProxy.getConnection()
 *         → DataSourceUtils.getConnection(dynamicRoutingDataSource)
 *           → 命中 / 创建事务绑定连接
 * </pre>
 *
 * <p>因此 jOOQ 与 MyBatis 在 @Transactional 内必然使用同一个物理连接。
 *
 * <p>数据源切换必须发生在事务开启之前（{@link DataSourceAspect} 或 DynamicProjectDataSourceAspect 的 @Order
 * 小于事务切面即可），事务内不允许再切。
 */
@Slf4j
@Component
public class JooqDataContextFactory {

    private final DSLContext dslContext;

    public JooqDataContextFactory(DynamicRoutingDataSource dynamicRoutingDataSource) {
        // 单例 TransactionAwareDataSourceProxy，target 必须是事务管理器绑定的那个实例
        TransactionAwareDataSourceProxy proxy =
                new TransactionAwareDataSourceProxy(dynamicRoutingDataSource);

        DefaultConfiguration config = new DefaultConfiguration();
        config.set(proxy);
        config.set(SQLDialect.MYSQL);

        // DSLContext 内部无状态，ConnectionProvider 每次 acquire 时走 proxy，
        // 因此缓存单例是线程安全的，并且每次执行 SQL 都会加入当前事务（若有）
        this.dslContext = DSL.using(config);

        log.info(
                "JooqDataContextFactory 初始化完成，target={}",
                dynamicRoutingDataSource.getClass().getName());
    }

    /** 事务内/事务外统一入口：数据源由 DataSourceContextHolder 决定。 */
    public DSLContext getContext() {
        return dslContext;
    }

    /**
     * 保留兼容签名。
     *
     * <p>事务内 projectNo 无实际作用——连接由事务上下文锁定； 事务外 projectNo 也不参与选择，路由完全由 DataSourceContextHolder 决定，
     * 调用方需自行保证在调用前已把 key 切到目标库。
     */
    public DSLContext getContext(String projectNo) {
        return dslContext;
    }
}
