package com.jdec.platform.data.api.model.predicate;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jdec.platform.data.api.dto.request.FilterDeserializer;
import java.io.Serializable;

/**
 * 查询谓词（Predicate）根抽象接口。
 *
 * <p>表示 SQL 抽象语法树（AST）中的布尔条件节点，彻底解耦“字段比较条件”与“逻辑复合容器”， 支持任意深度的嵌套、函数运算与布尔结合（AND / OR / NOT）。
 */
@JsonDeserialize(using = FilterDeserializer.class)
public interface Predicate extends Serializable {

    /** 获取谓词类型名称 */
    String getType();
}
