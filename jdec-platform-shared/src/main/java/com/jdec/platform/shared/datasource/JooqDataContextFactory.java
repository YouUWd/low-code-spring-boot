package com.jdec.platform.shared.datasource;

import com.jdec.platform.shared.context.AppContext;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;

/** jOOQ DSLContext 动态上下文工厂 负责根据当前 ThreadLocal 上下文或指定 projectNo 获取目标业务库的 DSLContext */
@Slf4j
@Component
@RequiredArgsConstructor
public class JooqDataContextFactory {

    private final DataSourceResolver dataSourceResolver;

    /** 获取当前 AppContext.getProjectNo() 对应的业务库 DSLContext */
    public DSLContext getContext() {
        String projectNo = AppContext.getProjectNo();
        return getContext(projectNo);
    }

    /** 根据指定的 projectNo 获取对应业务库的 DSLContext (支持 Spring @Transactional 事务同步与自动回滚) */
    public DSLContext getContext(String projectNo) {
        DataSource ds = dataSourceResolver.resolve(projectNo);
        DataSource transactionAwareDs =
                (ds instanceof TransactionAwareDataSourceProxy proxy)
                        ? proxy
                        : new TransactionAwareDataSourceProxy(ds);

        DefaultConfiguration config = new DefaultConfiguration();
        config.set(transactionAwareDs);
        config.set(SQLDialect.MYSQL);
        return DSL.using(config);
    }
}
