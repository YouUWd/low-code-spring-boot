package com.jdec.platform.config.biz.reference;

/**
 * 一条引用关系元数据。
 *
 * <p>表达「{@code sourceTable.sourceField} 引用 {@code targetTable.targetField}」， 由 {@link
 * ReferenceRegistry} 扫描 {@link Reference} 后生成。
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
        String targetField) {}
