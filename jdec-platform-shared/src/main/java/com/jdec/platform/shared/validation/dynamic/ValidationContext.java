package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 字段规则执行上下文。
 *
 * @param tree 请求树索引
 * @param current 当前记录
 * @param state 校验状态（含校验选项）
 * @param registry 模块定义注册表
 */
public record ValidationContext(
        RequestTree tree,
        RecordNode current,
        ValidationState state,
        ModuleDefinitionRegistry registry) {

    public String moduleId() {
        return current.moduleId();
    }

    public String recordPath() {
        return current.path();
    }

    public JsonNode currentField(String fieldKey) {
        return current.row() == null ? null : current.row().get(fieldKey);
    }
}
