package com.jdec.platform.data.api.model.predicate;

import java.util.Collection;
import java.util.List;

/**
 * 谓词 Fluent 静态工厂构建器 (Predicates)。
 *
 * <p>为上层业务提供类型安全、无二义性、语义极度清晰的条件构造入口。
 */
public final class Predicates {

    private Predicates() {}

    public static Predicate and(Predicate... predicates) {
        return CompoundPredicate.and(predicates);
    }

    public static Predicate and(List<Predicate> predicates) {
        return CompoundPredicate.and(predicates);
    }

    public static Predicate or(Predicate... predicates) {
        return CompoundPredicate.or(predicates);
    }

    public static Predicate or(List<Predicate> predicates) {
        return CompoundPredicate.or(predicates);
    }

    public static Predicate not(Predicate predicate) {
        return CompoundPredicate.not(predicate);
    }

    public static Predicate eq(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "EQ", value);
    }

    public static Predicate eq(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "EQ", value);
    }

    public static Predicate eq(FieldExpression expr, Object value) {
        return ComparisonPredicate.of(expr, "EQ", value);
    }

    public static Predicate neq(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "NEQ", value);
    }

    public static Predicate neq(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "NEQ", value);
    }

    public static Predicate in(String columnName, Collection<?> values) {
        return ComparisonPredicate.of(columnName, "IN", values);
    }

    public static Predicate in(String tableName, String columnName, Collection<?> values) {
        return ComparisonPredicate.of(tableName, columnName, "IN", values);
    }

    public static Predicate notIn(String columnName, Collection<?> values) {
        return ComparisonPredicate.of(columnName, "NOT_IN", values);
    }

    public static Predicate notIn(String tableName, String columnName, Collection<?> values) {
        return ComparisonPredicate.of(tableName, columnName, "NOT_IN", values);
    }

    public static Predicate like(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "LIKE", value);
    }

    public static Predicate like(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "LIKE", value);
    }

    public static Predicate gt(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "GT", value);
    }

    public static Predicate gt(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "GT", value);
    }

    public static Predicate gte(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "GTE", value);
    }

    public static Predicate gte(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "GTE", value);
    }

    public static Predicate lt(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "LT", value);
    }

    public static Predicate lt(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "LT", value);
    }

    public static Predicate lte(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "LTE", value);
    }

    public static Predicate lte(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "LTE", value);
    }

    public static Predicate isNull(String columnName) {
        return ComparisonPredicate.of(columnName, "IS_NULL", null);
    }

    public static Predicate isNull(String tableName, String columnName) {
        return ComparisonPredicate.of(tableName, columnName, "IS_NULL", null);
    }

    public static Predicate isNotNull(String columnName) {
        return ComparisonPredicate.of(columnName, "IS_NOT_NULL", null);
    }

    public static Predicate isNotNull(String tableName, String columnName) {
        return ComparisonPredicate.of(tableName, columnName, "IS_NOT_NULL", null);
    }

    public static Predicate between(String columnName, Object start, Object end) {
        return ComparisonPredicate.of(columnName, "BETWEEN", List.of(start, end));
    }

    public static Predicate between(String tableName, String columnName, Object start, Object end) {
        return ComparisonPredicate.of(tableName, columnName, "BETWEEN", List.of(start, end));
    }

    public static Predicate jsonContains(String columnName, Object value) {
        return ComparisonPredicate.of(columnName, "JSON_CONTAINS", value);
    }

    public static Predicate jsonContains(String tableName, String columnName, Object value) {
        return ComparisonPredicate.of(tableName, columnName, "JSON_CONTAINS", value);
    }

    public static Predicate raw(String sqlTemplate, Object... params) {
        return RawSqlPredicate.of(sqlTemplate, params);
    }
}
