package com.jdec.platform.externalobject.biz.service.support;

import java.util.LinkedList;
import java.util.List;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * 外部对象表及父子关系。
 *
 * <p>查重时从字段所在表沿 {@link #parent()} 回溯到 info 主表，再从 info 逐层下钻收集记录ID。
 */
public enum ExternalObjectTable {
    /** 外部对象主档 */
    INFO("info", null, null),

    /** 工商与法定资质表 */
    BASIC_INFO("basic_info", INFO, "company_id"),

    /** 组织与人员表 */
    MEMBER("member", INFO, "company_id");

    private final String tableName;

    private final ExternalObjectTable parent;

    /** 关联父表 id 的列名 */
    private final String parentIdColumn;

    ExternalObjectTable(String tableName, ExternalObjectTable parent, String parentIdColumn) {
        this.tableName = tableName;
        this.parent = parent;
        this.parentIdColumn = parentIdColumn;
    }

    public String tableName() {
        return tableName;
    }

    public ExternalObjectTable parent() {
        return parent;
    }

    public String parentIdColumn() {
        return parentIdColumn;
    }

    /** 按表名解析；不支持的表返回 {@code null}。 */
    public static ExternalObjectTable of(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        String name = tableName.trim();
        for (ExternalObjectTable table : values()) {
            if (table.tableName.equalsIgnoreCase(name)) {
                return table;
            }
        }
        return null;
    }

    /** info 到当前表的层级（不含 info，按由父到子排列）。 */
    public List<ExternalObjectTable> pathFromInfo() {
        LinkedList<ExternalObjectTable> path = new LinkedList<>();
        ExternalObjectTable current = this;
        while (current != null && current != INFO) {
            path.addFirst(current);
            current = current.parent();
        }
        return path;
    }

    public Table<?> table() {
        return DSL.table(DSL.name(tableName));
    }

    public Field<Long> longField(String column) {
        return DSL.field(DSL.name(tableName, column), Long.class);
    }

    public Field<String> textField(String column) {
        return DSL.field(DSL.name(tableName, column), String.class);
    }

    public Field<Integer> intField(String column) {
        return DSL.field(DSL.name(tableName, column), Integer.class);
    }
}
