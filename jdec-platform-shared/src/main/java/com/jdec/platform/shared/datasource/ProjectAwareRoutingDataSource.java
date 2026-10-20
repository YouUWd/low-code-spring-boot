package com.jdec.platform.shared.datasource;

import com.jdec.platform.shared.context.AppContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 项目感知路由：在 {@link DynamicRoutingDataSource} 的纯路由基础上， 当 {@link DataSourceContextHolder#get()} 为空时按
 * {@code AppContext.projectNo} 兜底。
 *
 * <p><b>只读不写：</b>{@link #determineCurrentLookupKey()} 是"探测"而非"切换"， 全程只调用 {@link
 * DataSourceContextHolder#get()}，<b>绝不</b>调用 {@code set()}。 如果在这里 set，每次 {@code getConnection()}
 * 都会压栈，栈会越积越深， 而且事务结束时无人 pop，最终污染 ThreadLocal。
 *
 * <p><b>路由优先级：</b>
 *
 * <ol>
 *   <li>{@link DataSourceContextHolder#get()} 非空 → 显式上下文优先 （{@code @DataSource}、{@code
 *       openMenuHeaderApi} 等手写 set、其他切面）；
 *   <li>否则 {@code AppContext.projectNo} 非空且解析出的 key 已注册 → 兜底；
 *   <li>否则返回 {@code null}，交由 {@code defaultTargetDataSource}（primary）。
 * </ol>
 */
@Slf4j
public class ProjectAwareRoutingDataSource extends DynamicRoutingDataSource {

    private final ProjectProperties projectProperties;

    public ProjectAwareRoutingDataSource(ProjectProperties projectProperties) {
        this.projectProperties = projectProperties;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        // 1) 显式上下文优先（同包可直接调用包内可见的 get()）
        String explicit = DataSourceContextHolder.get();
        if (StringUtils.isNotBlank(explicit)) {
            return explicit;
        }

        // 2) 项目号兜底
        String projectNo = AppContext.getProjectNo();
        if (StringUtils.isNotBlank(projectNo)) {
            String dsKey = projectProperties.getDataSourceKey(projectNo);
            if (StringUtils.isNotBlank(dsKey) && getDataSource(dsKey) != null) {
                if (log.isDebugEnabled()) {
                    log.debug("数据源兜底路由：projectNo={} → dsKey={}", projectNo, dsKey);
                }
                return dsKey;
            }
            log.warn("项目 {} 解析出的数据源 [{}] 未注册，回退到 defaultTargetDataSource", projectNo, dsKey);
        }

        // 3) 未匹配：走 defaultTargetDataSource
        return null;
    }
}
