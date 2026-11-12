package com.jdec.platform.config.biz.reference;

/**
 * 一条引用关系元数据。
 *
 * <p>表达「{@code sourceTable.sourceField} 引用 {@code targetTable.targetField}」， 由 {@link
 * ReferenceRegistry} 扫描 {@link Reference} 后生成。
 *
 * <p>{@code via*} 系列为可选的「关联展示」配置：源记录通过 {@code viaFkColumn} 关联 {@code viaTable}， 展示其 {@code
 * viaDisplayColumn} 列，用于在错误提示中呈现具体记录名称。
 */
public record ReferenceMetadata(
        Class<?> sourceEntity,
        String sourceTable,
        String sourceField,
        String sourcePk,
        String displayField,
        String name,
        Class<?> targetEntity,
        String targetTable,
        String targetField,
        String viaTable,
        String viaFkColumn,
        String viaPkColumn,
        String viaDisplayColumn) {

    /** 是否启用了关联展示（提示中可展示具体记录名称）。 */
    public boolean hasViaDisplay() {
        return viaTable != null
                && viaFkColumn != null
                && viaPkColumn != null
                && viaDisplayColumn != null;
    }
}
