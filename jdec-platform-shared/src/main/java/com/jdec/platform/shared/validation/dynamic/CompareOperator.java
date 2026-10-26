package com.jdec.platform.shared.validation.dynamic;

/** 字段比较运算符。 */
public enum CompareOperator {
    /** 等于期望值之一 */
    EQ,
    /** 不等于任何期望值 */
    NE,
    /** 在期望值集合中 */
    IN,
    /** 不在期望值集合中 */
    NOT_IN,
    /** 字段存在（非缺失且非 null） */
    EXISTS,
    /** 字段不存在（缺失或 null） */
    NOT_EXISTS
}
