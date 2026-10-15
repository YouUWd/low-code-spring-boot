package com.jdec.platform.data.api.model.predicate;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;

/**
 * 查询谓词（Predicate）根抽象接口。
 *
 * <p>表示 SQL 抽象语法树（AST）中的布尔条件节点，彻底解耦“字段比较条件”与“逻辑复合容器”， 支持任意深度的嵌套、函数运算与布尔结合（AND / OR / NOT）。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        defaultImpl = ComparisonPredicate.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ComparisonPredicate.class, name = "comparison"),
    @JsonSubTypes.Type(value = CompoundPredicate.class, name = "compound"),
    @JsonSubTypes.Type(value = RawSqlPredicate.class, name = "raw")
})
public interface Predicate extends Serializable {

    /** 获取谓词类型名称 */
    String getType();
}
