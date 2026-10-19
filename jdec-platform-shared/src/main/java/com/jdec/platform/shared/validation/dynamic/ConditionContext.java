package com.jdec.platform.shared.validation.dynamic;

/**
 * 条件求值上下文。
 *
 * @param tree 请求树索引
 * @param current 当前正在校验的记录
 * @param state 校验状态
 * @param registry 模块定义注册表
 */
public record ConditionContext(
        RequestTree tree,
        RecordNode current,
        ValidationState state,
        ModuleDefinitionRegistry registry) {}
