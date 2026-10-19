package com.jdec.platform.shared.validation.dynamic;

/**
 * 集合作用域的量词。
 *
 * <p>{@code ANY} 与 {@code ALL} 的业务含义差异很大，必须由产品规则明确，不能隐式选择。
 */
public enum CollectionQuantifier {
    /** 只检查第一条匹配记录 */
    FIRST,
    /** 只检查指定下标记录 */
    INDEX,
    /** 至少一条记录满足 */
    ANY,
    /** 全部记录都满足 */
    ALL
}
