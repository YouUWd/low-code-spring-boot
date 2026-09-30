package com.jdec.platform.common.biz.monitorlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.config.open.api.OpenMenuHeaderApi;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 操作日志字段权限过滤服务
 *
 * <p>用于过滤操作日志 {@code remark} 中记录的字段变更明细：
 *
 * <ol>
 *   <li>按菜单ID 解析出表头归属菜单（页面类型取关联的详情菜单），递归其全部子级菜单，收集各菜单绑定的模块ID；
 *   <li>按「模块ID列表 + 当前角色ID」查 {@code sys_role_module_field_permission}，取可查阅（{@code view=1}）的字段ID；
 *   <li>按字段ID 回 {@code sys_module_field} 取物理表名、列名，拼成 {@code 表名.字段名} 组成可查阅字段索引集合；
 *   <li>遍历日志 {@code remark} JSON 中的 {@code columns} 数组，剔除 {@code field} 不在集合内的明细对象。
 * </ol>
 *
 * <p>权限数据位于配置中心（config_center），与本服务所在业务库不同源，故通过 {@link DataSourceResolver} 显式指定数据源查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorLogFieldPermissionService {

    /** 配置中心菜单表 */
    private static final String TABLE_SYS_MENU = "sys_menu";

    /** 配置中心模块字段表 */
    private static final String TABLE_SYS_MODULE_FIELD = "sys_module_field";

    /** 配置中心角色模块字段权限表 */
    private static final String TABLE_SYS_ROLE_MODULE_FIELD_PERMISSION =
            "sys_role_module_field_permission";

    /** 可查阅标记：sys_role_module_field_permission.view = 1 */
    private static final Integer VIEW_YES = 1;

    /** 字段索引分隔符：表名.字段名 */
    private static final String INDEX_SEPARATOR = ".";

    /** 字段明细所在键名 */
    private static final String COLUMNS_KEY = "columns";

    /** 字段明细中的字段键名 */
    private static final String FIELD_KEY = "field";

    private final DataSourceResolver dataSourceResolver;
    private final OpenMenuHeaderApi openMenuHeaderApi;
    private final ObjectMapper objectMapper;

    /**
     * 查询当前角色在指定菜单子树内可查阅的字段索引集合（{@code 表名.字段名}）。
     *
     * @param menuId 列表菜单ID（内部按表头归属规则解析出详情菜单后组树）
     * @param roleId 当前角色ID；为空时返回 {@code null}，表示不做字段过滤
     * @return 可查阅字段索引集合；roleId 为空返回 {@code null}
     */
    public Set<String> readableFieldIndexes(Long menuId, Long roleId) {
        if (menuId == null || roleId == null) {
            return null;
        }
        Long rootMenuId = openMenuHeaderApi.resolveHeaderMenuId(menuId);
        DSLContext dsl = dataSourceResolver.getDSLContext(DataSourceConstants.CONFIG_CENTER);
        Set<Long> moduleIds = collectModuleIds(dsl, rootMenuId);
        if (moduleIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> fieldIds = selectReadableFieldIds(dsl, roleId, moduleIds);
        if (fieldIds.isEmpty()) {
            return Collections.emptySet();
        }
        return selectFieldIndexes(dsl, fieldIds);
    }

    /**
     * 过滤单条日志 {@code remark} 中的字段明细。
     *
     * <p>递归遍历 JSON，对每个名为 {@code columns} 的数组，剔除其中 {@code field} 不在可查阅集合内的元素。 {@code
     * allowedFieldIndexes} 为 {@code null} 时原样返回（不做过滤）。
     *
     * @param remark 日志备注对象（Map / List 结构）
     * @param allowedFieldIndexes 可查阅字段索引集合，null 表示不过滤
     * @return 过滤后的备注对象
     */
    public Object filterRemark(Object remark, Set<String> allowedFieldIndexes) {
        if (remark == null || allowedFieldIndexes == null) {
            return remark;
        }
        JsonNode root = objectMapper.valueToTree(remark);
        filterColumns(root, allowedFieldIndexes);
        return objectMapper.convertValue(root, Object.class);
    }

    // ==================== 权限查询 ====================

    /** 递归收集菜单子树内各菜单绑定的模块ID（已过滤 0 与 null） */
    private Set<Long> collectModuleIds(DSLContext dsl, Long menuId) {
        Set<Long> moduleIds = new LinkedHashSet<>();
        collectModuleIds(dsl, menuId, moduleIds, new HashSet<>());
        return moduleIds;
    }

    private void collectModuleIds(
            DSLContext dsl, Long menuId, Set<Long> moduleIds, Set<Long> visited) {
        if (menuId == null || !visited.add(menuId)) {
            return;
        }
        Field<Long> idField = DSL.field(DSL.name("id"), Long.class);
        Field<Long> pidField = DSL.field(DSL.name("pid"), Long.class);
        Field<Long> moduleIdField = DSL.field(DSL.name("module_id"), Long.class);

        Long moduleId =
                dsl.select(moduleIdField)
                        .from(DSL.table(DSL.name(TABLE_SYS_MENU)))
                        .where(idField.eq(menuId).and(tenantCondition()))
                        .fetchOne(moduleIdField);
        if (moduleId != null && moduleId != 0) {
            moduleIds.add(moduleId);
        }
        List<Long> childIds =
                dsl.select(idField)
                        .from(DSL.table(DSL.name(TABLE_SYS_MENU)))
                        .where(pidField.eq(menuId).and(tenantCondition()))
                        .fetch(idField);
        for (Long childId : childIds) {
            collectModuleIds(dsl, childId, moduleIds, visited);
        }
    }

    /** 按「角色 + 模块 + view=1」查模块字段ID（忽略模块级记录 module_field_id=0） */
    private List<Long> selectReadableFieldIds(DSLContext dsl, Long roleId, Set<Long> moduleIds) {
        Field<Long> moduleFieldIdField = DSL.field(DSL.name("module_field_id"), Long.class);
        return dsl
                .select(moduleFieldIdField)
                .from(DSL.table(DSL.name(TABLE_SYS_ROLE_MODULE_FIELD_PERMISSION)))
                .where(
                        DSL.field(DSL.name("role_id"), Long.class)
                                .eq(roleId)
                                .and(DSL.field(DSL.name("module_id"), Long.class).in(moduleIds))
                                .and(DSL.field(DSL.name("view"), Integer.class).eq(VIEW_YES))
                                .and(moduleFieldIdField.gt(0L)))
                .fetch(moduleFieldIdField)
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 按字段ID 查物理表名、列名，拼成 {@code 表名.字段名} 集合 */
    private Set<String> selectFieldIndexes(DSLContext dsl, List<Long> fieldIds) {
        Field<Long> idField = DSL.field(DSL.name("id"), Long.class);
        Field<String> tableField = DSL.field(DSL.name("table_name"), String.class);
        Field<String> columnField = DSL.field(DSL.name("column_name"), String.class);
        return dsl
                .select(tableField, columnField)
                .from(DSL.table(DSL.name(TABLE_SYS_MODULE_FIELD)))
                .where(idField.in(fieldIds))
                .fetch()
                .stream()
                .map(record -> record.get(tableField) + INDEX_SEPARATOR + record.get(columnField))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 菜单数据隔离条件：subject_id + project_no（为空时不限定） */
    private Condition tenantCondition() {
        Condition condition = DSL.noCondition();
        Long subjectId = AppContext.getSubjectId();
        if (subjectId != null) {
            condition = condition.and(DSL.field(DSL.name("subject_id"), Long.class).eq(subjectId));
        }
        String projectNo = AppContext.getProjectNo();
        if (projectNo != null && !projectNo.trim().isEmpty()) {
            condition =
                    condition.and(DSL.field(DSL.name("project_no"), String.class).eq(projectNo));
        }
        return condition;
    }

    // ==================== remark 过滤 ====================

    /** 递归遍历 JSON，过滤 columns 数组中的字段明细 */
    private void filterColumns(JsonNode node, Set<String> allowedFieldIndexes) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode columns = object.get(COLUMNS_KEY);
            if (columns != null && columns.isArray()) {
                ArrayNode kept = objectMapper.createArrayNode();
                for (JsonNode element : columns) {
                    JsonNode field = element.get(FIELD_KEY);
                    // field 有值且不在可查阅集合内 → 剔除该明细；field 缺失则保留
                    if (field != null
                            && field.isTextual()
                            && !allowedFieldIndexes.contains(field.asText())) {
                        continue;
                    }
                    kept.add(element);
                }
                object.set(COLUMNS_KEY, kept);
            }
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                filterColumns(object.get(name), allowedFieldIndexes);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                filterColumns(element, allowedFieldIndexes);
            }
        }
    }
}
