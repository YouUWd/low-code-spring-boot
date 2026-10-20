package com.jdec.platform.shared.datasource;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源路由 — Spring 标准 {@link AbstractRoutingDataSource} 实现。
 *
 * <p>根据 {@link DataSourceContextHolder} 中当前线程绑定的数据源 key 决定实际使用哪个物理 DataSource。
 *
 * <p><b>实现 {@link DisposableBean} 的原因：</b> {@link DynamicDataSourceConfig#dataSource()} 中手工 new 的
 * {@code HikariDataSource} 并没有注册为 Spring Bean（只放进了 {@code targetDataSources} Map）， 容器关闭时 Spring
 * 不会主动调用它们的 {@code close()}。 结果就是：Tomcat 线程池停止后，Hikari 后台线程还在，JVM 可能 hang 住不退出， MySQL
 * 侧也会残留连接。这里统一在容器关闭时关闭所有物理数据源。
 */
@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource implements DisposableBean {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }

    /**
     * 根据数据源名称获取物理 DataSource。
     *
     * <p>利用父类 {@link AbstractRoutingDataSource} 维护的 resolvedDataSources Map。 返回 {@code null}
     * 表示数据源不存在。
     */
    public DataSource getDataSource(String name) {
        Map<Object, DataSource> map = getResolvedDataSourcesSafely();
        return map == null ? null : map.get(name);
    }

    /**
     * 容器关闭时依次关闭所有物理数据源。
     *
     * <p>要点：
     *
     * <ul>
     *   <li>遍历 {@code resolvedDataSources}（即 {@code afterPropertiesSet()} 解析后的 Map）， 其 value 就是配置类中
     *       new 出来的 HikariDataSource；
     *   <li>用 {@link IdentityHashMap} 按引用去重——{@code defaultTargetDataSource} 与 {@code
     *       targetDataSources} 中的某个实例是同一对象，避免重复关闭；
     *   <li>单个数据源关闭失败不影响其余数据源关闭；
     *   <li>HikariDataSource.close() 本身幂等，多关几次也不会报错。
     * </ul>
     */
    @Override
    public void destroy() throws Exception {
        Map<Object, DataSource> dataSources = getResolvedDataSourcesSafely();
        if (dataSources == null || dataSources.isEmpty()) {
            log.debug("动态数据源关闭：无物理数据源需要关闭");
            return;
        }

        Set<DataSource> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<Object, DataSource> entry : dataSources.entrySet()) {
            DataSource ds = entry.getValue();
            if (ds == null || !closed.add(ds)) {
                continue;
            }
            if (ds instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                    log.info("关闭物理数据源: {}", entry.getKey());
                } catch (Exception e) {
                    log.warn("关闭物理数据源失败: {}", entry.getKey(), e);
                }
            } else {
                log.debug("物理数据源 {} 不支持 AutoCloseable，跳过关闭", entry.getKey());
            }
        }
        log.info("动态数据源全部关闭完成，共 {} 个", closed.size());
    }

    /**
     * 安全调用父类 {@code getResolvedDataSources()}。
     *
     * <p>父类实现在 {@code afterPropertiesSet()} 之前会抛 {@link IllegalStateException}， 这里捕获后返回 {@code
     * null}，让调用方按"未初始化"处理。
     */
    private Map<Object, DataSource> getResolvedDataSourcesSafely() {
        try {
            return getResolvedDataSources();
        } catch (IllegalStateException ex) {
            log.debug("动态数据源尚未初始化（afterPropertiesSet 未执行），当前无 resolvedDataSources");
            return null;
        }
    }
}
