package com.jdec.platform.data.api.model.predicate;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;

/**
 * 字段/列表达式接口（Expression）。
 *
 * <p>支持普通物理列引用、跨表列引用、以及高阶函数包装（如 JSON_EXTRACT, DISTINCT, COALESCE 等）。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "exprType",
        defaultImpl = ColumnRef.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ColumnRef.class, name = "column"),
    @JsonSubTypes.Type(value = FunctionExpr.class, name = "function")
})
public interface FieldExpression extends Serializable {

    /** 表达式类型 */
    String getExprType();
}
