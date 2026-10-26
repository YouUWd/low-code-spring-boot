package com.jdec.platform.config.biz.audit.service;

import static org.jooq.impl.DSL.*;

import cn.hutool.core.util.StrUtil;
import com.jdec.platform.shared.context.AppContext;
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

/**
 * 字典字段查询服务
 *
 * <p>用于审计日志中字典值转中文描述
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class DictFieldQueryService {

    private final DataSourceResolver dataSourceResolver;

    /**
     * 根据字典分类别名和值列表获取展示字段值
     *
     * <p>默认按 {@code label} 字段映射（字典值转中文label）；如果指定了 {@code nameField}，则取该字段对应的列进行映射， 例如字典配置项通过
     * {@code extra} 列存放图标、颜色等扩展属性。
     *
     * @param categoryAlias 字典分类别名（对应 sys_config_category.category_alias）
     * @param values 字典值列表（对应 sys_config_item.value）
     * @param nameField 展示字段名（对应 sys_config_item 中的列名，为空时默认使用 label）
     * @return 字典值 -> 展示字段值的映射
     */
    public Map<String, String> queryLabelsByValues(
            String categoryAlias, List<String> values, String nameField) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }

        // 默认使用 label 字段进行映射，指定了 nameField 则使用配置的字段
        String displayField = StrUtil.blankToDefault(nameField, "label");

        try {
            String projectNo = AppContext.getProjectNo();
            Long subjectId = AppContext.getSubjectId();

            DSLContext dsl = dataSourceResolver.getDSLContext(DataSourceConstants.CONFIG_CENTER);
            Field<String> valueField = field(name("value"), String.class);
            Field<String> displayFieldDef = field(name(displayField), String.class);

            var result =
                    dsl.select(valueField, displayFieldDef)
                            .from(table("sys_config_item"))
                            .where(valueField.in(values))
                            .and(field(name("category_alias")).eq(categoryAlias))
                            .and(field(name("project_no")).eq(projectNo))
                            .and(field(name("subject_id")).eq(subjectId))
                            .and(field(name("status")).eq(1))
                            .fetch();

            if (result.isEmpty()) {
                log.warn(
                        "字典配置项不存在: categoryAlias={}, projectNo={}, subjectId={}, values={}",
                        categoryAlias,
                        projectNo,
                        subjectId,
                        values);
                return Collections.emptyMap();
            }

            // 构建 value -> 展示字段 映射
            return result.stream()
                    .filter(r -> r.get(valueField) != null && r.get(displayFieldDef) != null)
                    .collect(
                            Collectors.toMap(
                                    r -> r.get(valueField),
                                    r -> r.get(displayFieldDef),
                                    (v1, v2) -> v1));

        } catch (Exception e) {
            log.error("查询字典描述失败: categoryAlias={}, values={}", categoryAlias, values, e);
            return Collections.emptyMap();
        }
    }
}
