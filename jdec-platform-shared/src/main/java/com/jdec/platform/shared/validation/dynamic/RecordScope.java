package com.jdec.platform.shared.validation.dynamic;

/**
 * 控制字段所在记录的查找范围。
 *
 * <ul>
 *   <li>{@link #CURRENT}：当前记录
 *   <li>{@link #PARENT}：直接父记录
 *   <li>{@link #ANCESTOR}：向上第 N 层祖先记录（或最近的指定模块祖先）
 *   <li>{@link #ROOT}：请求树的根记录集合
 *   <li>{@link #SIBLING_RECORDS}：与当前记录共享父记录、且同模块的记录集合
 *   <li>{@link #CHILD_MODULE_RECORDS}：当前记录下的直接子模块记录集合
 *   <li>{@link #MODULE_RECORDS}：整棵请求树中指定模块的全部记录
 *   <li>{@link #ALL_RECORDS}：整棵请求树的全部记录（谨慎使用）
 * </ul>
 */
public enum RecordScope {
    CURRENT,
    PARENT,
    ANCESTOR,
    ROOT,
    SIBLING_RECORDS,
    CHILD_MODULE_RECORDS,
    MODULE_RECORDS,
    ALL_RECORDS
}
