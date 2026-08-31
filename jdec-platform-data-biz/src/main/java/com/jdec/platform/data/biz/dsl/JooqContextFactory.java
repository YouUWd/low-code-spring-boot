package com.jdec.platform.data.biz.dsl;

import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** jOOQ DSLContext 动态上下文工厂 负责根据当前 ThreadLocal 上下文或指定 projectNo 获取目标业务库的 DSLContext */
@Slf4j
@Component
@RequiredArgsConstructor
public class JooqContextFactory {

    private final DataSourceResolver dataSourceResolver;

    /** 获取当前 AppContext.getProjectNo() 对应的业务库 DSLContext */
    public DSLContext getContext() {
        String projectNo = AppContext.getProjectNo();
        return getContext(projectNo);
    }

    /** 根据指定的 projectNo 获取对应业务库的 DSLContext */
    public DSLContext getContext(String projectNo) {
        DataSource ds = dataSourceResolver.resolve(projectNo);
        return DSL.using(ds, SQLDialect.MYSQL);
    }
}
