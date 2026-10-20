package com.jdec.platform.shared.datasource;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 数据源解析器 —— 保留原有方法签名，内部对事务场景做了兼容。
 *
 * <p><b>双模式行为：</b>
 *
 * <ul>
 *   <li><b>事务内</b>（{@code isActualTransactionActive() == true}）：返回持有 {@link
 *       DynamicRoutingDataSource} 的事务代理，jOOQ / JdbcTemplate 会通过 {@code
 *       DataSourceUtils.getConnection(dynamicRoutingDataSource)} 命中事务绑定连接， 与外层 Spring 事务完全一致。
 *   <li><b>事务外</b>：返回持有“projectNo 对应物理池”的事务代理，连接精确落到该物理库， 用完即 release，不污染 ThreadLocal。
 * </ul>
 *
 * <p><b>语义变化提示：</b>事务内调用 {@code resolve(projectNo)} 时，连接由事务上下文决定， 不再保证落在 projectNo 对应的物理库。这是 Spring
 * 事务模型本身的约束（事务一旦开启， 连接已锁定），不是本类的缺陷。如需“事务内精确到某个库”，请在事务开启前设置好 {@link DataSourceContextHolder}（通过
 * {@link DataSourceAspect} 或 DynamicProjectDataSourceAspect）。
 */
@Slf4j
@Component
public class DataSourceResolver {

    private final ProjectProperties projectProperties;
    private final DynamicRoutingDataSource dynamicRoutingDataSource;

    /** 事务内统一使用：target=DynamicRoutingDataSource，命中事务绑定连接 */
    private final TransactionAwareDataSourceProxy transactionAwareRoutingDataSource;

    /** 事务外按物理池缓存 proxy，避免重复创建（key=physicalDs 实例） */
    private final ConcurrentMap<DataSource, TransactionAwareDataSourceProxy> proxyCache =
            new ConcurrentHashMap<>();

    public DataSourceResolver(
            ProjectProperties projectProperties,
            DynamicRoutingDataSource dynamicRoutingDataSource) {
        this.projectProperties = projectProperties;
        this.dynamicRoutingDataSource = dynamicRoutingDataSource;
        this.transactionAwareRoutingDataSource =
                new TransactionAwareDataSourceProxy(dynamicRoutingDataSource);
        log.info(
                "DataSourceResolver 初始化完成，routingDataSource={}",
                dynamicRoutingDataSource.getClass().getName());
    }

    /**
     * 根据 projectNo 解析数据源（事务内 → routing 代理；事务外 → 物理池代理）。
     *
     * <p>原方法签名与语义兼容：事务外仍精确返回 projectNo 对应的物理池， 事务内则自动升级为 routing 代理以加入 Spring 事务。
     */
    public DataSource resolve(String projectNo) {
        String dsName = projectProperties.getDatasourceMapping().getOrDefault(projectNo, projectNo);
        return resolveByDsName(dsName);
    }

    /**
     * 根据数据源名称构建 DSLContext（事务内 → routing 代理；事务外 → 物理池代理）。
     *
     * <p>签名保持不变，内部自动事务感知。
     */
    public DSLContext getDSLContext(String dsName) {
        return DSL.using(resolveByDsName(dsName), SQLDialect.MYSQL);
    }

    /**
     * 根据 projectNo 构建 DSLContext（事务内 → routing 代理；事务外 → 物理池代理）。
     *
     * <p>签名保持不变，内部自动事务感知。
     */
    public DSLContext getDSLContextByProjectNo(String projectNo) {
        return DSL.using(resolve(projectNo), SQLDialect.MYSQL);
    }

    /**
     * 根据 projectNo 构建 JdbcTemplate（事务内 → routing 代理；事务外 → 物理池代理）。
     *
     * <p>JdbcTemplate 内部使用 DataSourceUtils.getConnection，代理链路会正确命中事务绑定连接。
     */
    public JdbcTemplate getJdbcTemplate(String projectNo) {
        return new JdbcTemplate(resolve(projectNo));
    }

    // ==================== 内部统一入口 ====================

    /**
     * 事务内 / 事务外双模式分流。
     *
     * <ul>
     *   <li>事务内：返回 {@link #transactionAwareRoutingDataSource}，连接由路由 + 事务上下文决定；
     *   <li>事务外：返回 projectNo 对应物理池的缓存 proxy，连接精确落到目标库。
     * </ul>
     */
    private DataSource resolveByDsName(String dsName) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("resolveByDsName({}) → 事务内，使用 routing 事务代理", dsName);
            return transactionAwareRoutingDataSource;
        }

        DataSource physical = dynamicRoutingDataSource.getDataSource(dsName);
        if (physical == null) {
            log.error("数据源不存在: {}", dsName);
            throw new BusinessException("系统配置错误：找不到对应的数据源 [" + dsName + "]");
        }
        return proxyCache.computeIfAbsent(physical, TransactionAwareDataSourceProxy::new);
    }
}
