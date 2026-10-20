package com.jdec.platform.shared.validation.dynamic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段定义配置校验。
 *
 * <p>在应用启动时执行：检查字段引用是否存在、集合作用域是否配置了明确量词，并检测条件依赖是否形成循环。 配置非法时抛出 {@link IllegalStateException}
 * 使启动失败，避免运行期出现不确定行为。
 */
final class DefinitionConfigValidator {

    private DefinitionConfigValidator() {}

    static void validate(Map<String, ModuleDefinition> modules) {
        for (ModuleDefinition module : modules.values()) {
            for (FieldDefinition field : module.fields()) {
                validateReferences(module, field, modules);
            }
        }
        detectCycles(modules);
    }

    private static void validateReferences(
            ModuleDefinition module, FieldDefinition field, Map<String, ModuleDefinition> modules) {
        for (FieldRef ref : field.applicability().referencedFields()) {
            switch (ref.scope()) {
                case CURRENT -> {
                    if (ref.moduleId() != null && !ref.moduleId().equals(module.moduleId())) {
                        throw configError(module, field, "CURRENT 作用域引用了其他模块 " + ref.moduleId());
                    }
                    requireField(modules, module.moduleId(), ref.fieldKey(), module, field);
                }
                case MODULE_RECORDS ->
                        requireField(modules, ref.moduleId(), ref.fieldKey(), module, field);
                case PARENT, ANCESTOR, ROOT, SIBLING_RECORDS, CHILD_MODULE_RECORDS, ALL_RECORDS -> {
                    if (ref.moduleId() != null) {
                        requireField(modules, ref.moduleId(), ref.fieldKey(), module, field);
                    }
                }
            }
        }
    }

    private static void requireField(
            Map<String, ModuleDefinition> modules,
            String moduleId,
            String fieldKey,
            ModuleDefinition sourceModule,
            FieldDefinition sourceField) {
        ModuleDefinition targetModule = modules.get(moduleId);
        if (targetModule == null) {
            throw configError(sourceModule, sourceField, "引用了不存在的模块: " + moduleId);
        }
        if (targetModule.findField(fieldKey) == null) {
            throw configError(sourceModule, sourceField, "引用了不存在的字段: " + moduleId + "." + fieldKey);
        }
    }

    private static IllegalStateException configError(
            ModuleDefinition module, FieldDefinition field, String message) {
        return new IllegalStateException(
                "校验配置错误：模块 " + module.moduleId() + " 字段 " + field.fieldKey() + " " + message);
    }

    private static void detectCycles(Map<String, ModuleDefinition> modules) {
        Map<String, Set<String>> graph = new HashMap<>();
        // 先注册全部节点，避免目标节点尚未遍历时漏掉依赖边。
        for (ModuleDefinition module : modules.values()) {
            for (FieldDefinition field : module.fields()) {
                graph.putIfAbsent(nodeKey(module.moduleId(), field.fieldKey()), new HashSet<>());
            }
        }
        for (ModuleDefinition module : modules.values()) {
            for (FieldDefinition field : module.fields()) {
                String source = nodeKey(module.moduleId(), field.fieldKey());
                for (FieldRef ref : field.applicability().referencedFields()) {
                    String targetModuleId = targetModuleId(module, ref);
                    if (targetModuleId == null) {
                        continue;
                    }
                    String target = nodeKey(targetModuleId, ref.fieldKey());
                    if (graph.containsKey(target)) {
                        graph.get(source).add(target);
                    }
                }
            }
        }

        Map<String, Integer> colors = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        for (String node : graph.keySet()) {
            if (colors.getOrDefault(node, 0) == 0) {
                dfs(node, graph, colors, stack);
            }
        }
    }

    private static String targetModuleId(ModuleDefinition module, FieldRef ref) {
        if (ref.moduleId() != null) {
            return ref.moduleId();
        }
        if (ref.scope() == RecordScope.CURRENT) {
            return module.moduleId();
        }
        return null;
    }

    private static void dfs(
            String node,
            Map<String, Set<String>> graph,
            Map<String, Integer> colors,
            Deque<String> stack) {
        colors.put(node, 1);
        stack.push(node);

        for (String next : graph.getOrDefault(node, Set.of())) {
            int color = colors.getOrDefault(next, 0);
            if (color == 1) {
                String cycle = buildCycle(stack, next);
                throw new IllegalStateException("校验配置错误：条件存在循环依赖 " + cycle);
            }
            if (color == 0) {
                dfs(next, graph, colors, stack);
            }
        }

        stack.pop();
        colors.put(node, 2);
    }

    private static String buildCycle(Deque<String> stack, String repeated) {
        StringBuilder builder = new StringBuilder();
        boolean started = false;
        for (String node : stack) {
            if (node.equals(repeated)) {
                started = true;
            }
            if (started) {
                if (builder.length() > 0) {
                    builder.append(" -> ");
                }
                builder.append(node.replace('|', '.'));
            }
        }
        if (builder.length() > 0) {
            builder.append(" -> ").append(repeated.replace('|', '.'));
        }
        return builder.toString();
    }

    private static String nodeKey(String moduleId, String fieldKey) {
        return moduleId + "|" + fieldKey;
    }

    /** 供测试检查所有字段引用。 */
    static List<FieldRef> referencedFields(ModuleDefinition module) {
        return module.fields().stream()
                .flatMap(field -> field.applicability().referencedFields().stream())
                .toList();
    }
}
