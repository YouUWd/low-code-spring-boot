package com.jdec.platform.config.biz.audit.service;

import static org.jooq.impl.DSL.*;

import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Service;

/** 关联字段查询服务 - 用于获取关联表的中文名称 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class RelationFieldQueryService {

    private final DataSourceResolver dataSourceResolver;

    /**
     * 根据ID列表查询关联表的名称字段
     *
     * @param tableName 表名
     * @param idField ID字段名
     * @param nameField 名称字段名
     * @param ids ID列表
     * @return ID到名称的映射
     */
    public Map<Long, String> queryNameByIds(
            String tableName, String idField, String nameField, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            DSLContext dsl = dataSourceResolver.getDSLContext(DataSourceConstants.CONFIG_CENTER);

            Field<Long> idFieldDef = field(name(idField), Long.class);
            Field<String> nameFieldDef = field(name(nameField), String.class);

            var result =
                    dsl.select(idFieldDef, nameFieldDef)
                            .from(table(name(tableName)))
                            .where(idFieldDef.in(ids))
                            .fetch();

            return result.stream()
                    .collect(
                            Collectors.toMap(
                                    r -> r.get(idFieldDef),
                                    r -> r.get(nameFieldDef),
                                    (v1, v2) -> v1));

        } catch (Exception e) {
            log.error("查询关联字段失败: table={}, ids={}", tableName, ids, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 根据单个ID查询关联表的名称字段
     *
     * @param tableName 表名
     * @param idField ID字段名
     * @param nameField 名称字段名
     * @param id ID
     * @return 名称
     */
    public String queryNameById(String tableName, String idField, String nameField, Long id) {
        if (id == null) {
            return null;
        }

        Map<Long, String> result =
                queryNameByIds(tableName, idField, nameField, Collections.singletonList(id));
        return result.get(id);
    }
}
