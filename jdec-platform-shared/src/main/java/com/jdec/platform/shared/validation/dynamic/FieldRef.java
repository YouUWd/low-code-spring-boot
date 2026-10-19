package com.jdec.platform.shared.validation.dynamic;

import java.util.Objects;

/**
 * 控制字段引用，明确“到哪里读取控制字段”。
 *
 * <p>{@code fieldKey} 是字段定义中的逻辑标识；读取记录 row 时优先按原样匹配，其次兼容 {@code 表名.列名} 形式（例如 {@code
 * company.company_type}）。
 *
 * @param scope 查找范围
 * @param moduleId 目标模块过滤，可为空
 * @param fieldKey 控制字段标识
 * @param ancestorDepth 祖先层数；{@code -1} 表示向上查找最近的指定模块祖先
 * @param quantifier 集合量词
 * @param recordIndex {@code INDEX} 量词使用的下标
 */
public record FieldRef(
        RecordScope scope,
        String moduleId,
        String fieldKey,
        int ancestorDepth,
        CollectionQuantifier quantifier,
        int recordIndex) {

    public FieldRef {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("fieldKey 不能为空");
        }
        moduleId = moduleId == null || moduleId.isBlank() ? null : moduleId;
        if (quantifier == null) {
            quantifier = CollectionQuantifier.FIRST;
        }
        if (recordIndex < 0) {
            throw new IllegalArgumentException("recordIndex 不能小于 0");
        }
        if (scope == RecordScope.ANCESTOR) {
            if (ancestorDepth == 0 || ancestorDepth < -1) {
                throw new IllegalArgumentException(
                        "ANCESTOR 的 ancestorDepth 必须大于 0，或使用 -1 表示最近匹配模块");
            }
            if (ancestorDepth == -1 && moduleId == null) {
                throw new IllegalArgumentException("ANCESTOR 使用 -1 时必须指定 moduleId");
            }
        }
        if (scope == RecordScope.MODULE_RECORDS && moduleId == null) {
            throw new IllegalArgumentException("MODULE_RECORDS 必须指定 moduleId");
        }
        if (scope == RecordScope.CURRENT && quantifier != CollectionQuantifier.FIRST) {
            throw new IllegalArgumentException("CURRENT 只对应一条记录，量词必须为 FIRST");
        }
    }

    /** 当前记录的字段。 */
    public static FieldRef current(String fieldKey) {
        return new FieldRef(RecordScope.CURRENT, null, fieldKey, 0, CollectionQuantifier.FIRST, 0);
    }

    /** 直接父记录的字段。 */
    public static FieldRef parent(String moduleId, String fieldKey) {
        return new FieldRef(
                RecordScope.PARENT, moduleId, fieldKey, 0, CollectionQuantifier.FIRST, 0);
    }

    /** 向上第 N 层祖先记录的字段。 */
    public static FieldRef ancestor(int depth, String moduleId, String fieldKey) {
        return new FieldRef(
                RecordScope.ANCESTOR, moduleId, fieldKey, depth, CollectionQuantifier.FIRST, 0);
    }

    /** 向上查找最近的指定模块祖先记录的字段。 */
    public static FieldRef nearestAncestor(String moduleId, String fieldKey) {
        return new FieldRef(
                RecordScope.ANCESTOR, moduleId, fieldKey, -1, CollectionQuantifier.FIRST, 0);
    }

    /** 根记录集合的字段，必须显式声明量词。 */
    public static FieldRef root(
            CollectionQuantifier quantifier, String moduleId, String fieldKey, int recordIndex) {
        return new FieldRef(RecordScope.ROOT, moduleId, fieldKey, 0, quantifier, recordIndex);
    }

    /** 同父同模块兄弟记录集合的字段。 */
    public static FieldRef sibling(
            CollectionQuantifier quantifier, String moduleId, String fieldKey, int recordIndex) {
        return new FieldRef(
                RecordScope.SIBLING_RECORDS, moduleId, fieldKey, 0, quantifier, recordIndex);
    }

    /** 当前记录下直接子模块记录集合的字段。 */
    public static FieldRef childModule(
            CollectionQuantifier quantifier, String moduleId, String fieldKey, int recordIndex) {
        return new FieldRef(
                RecordScope.CHILD_MODULE_RECORDS, moduleId, fieldKey, 0, quantifier, recordIndex);
    }

    /** 整棵请求树中指定模块记录集合的字段。 */
    public static FieldRef module(
            CollectionQuantifier quantifier, String moduleId, String fieldKey, int recordIndex) {
        return new FieldRef(
                RecordScope.MODULE_RECORDS, moduleId, fieldKey, 0, quantifier, recordIndex);
    }

    /** 指定模块中任意一条记录的字段。 */
    public static FieldRef moduleAny(String moduleId, String fieldKey) {
        return module(CollectionQuantifier.ANY, moduleId, fieldKey, 0);
    }

    /** 指定模块中全部记录的字段。 */
    public static FieldRef moduleAll(String moduleId, String fieldKey) {
        return module(CollectionQuantifier.ALL, moduleId, fieldKey, 0);
    }

    /** 整棵请求树全部记录集合的字段。 */
    public static FieldRef all(CollectionQuantifier quantifier, String fieldKey, int recordIndex) {
        return new FieldRef(RecordScope.ALL_RECORDS, null, fieldKey, 0, quantifier, recordIndex);
    }
}
