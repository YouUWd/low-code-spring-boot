package com.jdec.platform.shared.validation.dynamic;

/**
 * 校验模式，用于区分缺失字段的语义。
 *
 * <ul>
 *   <li>{@link #CREATE}：新增，缺失字段按必填规则处理
 *   <li>{@link #UPDATE}：更新，缺失字段表示“不修改”，显式 {@code null} 表示“清空”
 *   <li>{@link #DRAFT}：草稿，跳过必填检测
 *   <li>{@link #SUBMIT}：正式提交（默认），执行完整必填检测
 * </ul>
 */
public enum ValidationMode {
    CREATE,
    UPDATE,
    DRAFT,
    SUBMIT
}
