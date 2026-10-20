package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 请求树构建器。
 *
 * <p>支持以下请求形态：
 *
 * <ul>
 *   <li>{@code {"nestedModuleData": {...}}}（本平台 DynamicSaveReq 的形态）
 *   <li>{@code {"nestedModuleData": [...]}}（多根模块）
 *   <li>直接传入模块对象 {@code {"moduleId": ..., "record": [...]}}
 *   <li>直接传入模块数组
 * </ul>
 *
 * <p>记录 row 支持扁平宽行（{@code "company.company_type"}）或按表分组的结构。
 */
public final class RequestTreeBuilder {

    private RequestTreeBuilder() {}

    public static RequestTree build(
            JsonNode request, ValidationState state, ValidationOptions options) {
        BuildContext context = new BuildContext(state, options);

        if (request == null || request.isNull()) {
            state.addError("$", null, null, ValidationErrorCodes.INVALID_REQUEST, "请求体不能为空");
            return new RequestTree(List.of(), List.of());
        }

        JsonNode container;
        String basePath;
        if (request.isArray()) {
            container = request;
            basePath = "nestedModuleData";
        } else if (request.isObject() && request.has("nestedModuleData")) {
            container = request.get("nestedModuleData");
            basePath = "nestedModuleData";
        } else if (request.isObject()) {
            container = request;
            basePath = "nestedModuleData";
        } else {
            state.addError(
                    "$", null, null, ValidationErrorCodes.INVALID_REQUEST, "请求体必须是 JSON 对象或数组");
            return new RequestTree(List.of(), List.of());
        }

        if (container.isArray()) {
            for (int i = 0; i < container.size(); i++) {
                buildModule(container.get(i), null, basePath + "[" + i + "]", 1, context);
            }
        } else if (container.isObject()) {
            buildModule(container, null, basePath, 1, context);
        } else {
            state.addError(
                    basePath,
                    null,
                    null,
                    ValidationErrorCodes.INVALID_REQUEST,
                    "nestedModuleData 必须是对象或数组");
        }

        return new RequestTree(context.roots, context.allRecords);
    }

    private static void buildModule(
            JsonNode moduleNode,
            RecordNode parent,
            String modulePath,
            int depth,
            BuildContext context) {
        if (depth > context.options.maxDepth()) {
            context.state.addError(
                    modulePath,
                    null,
                    null,
                    ValidationErrorCodes.MAX_DEPTH_EXCEEDED,
                    "嵌套层级超过限制 " + context.options.maxDepth());
            return;
        }
        if (moduleNode == null || !moduleNode.isObject()) {
            context.state.addError(
                    modulePath, null, null, ValidationErrorCodes.INVALID_REQUEST, "模块节点必须是对象");
            return;
        }

        JsonNode moduleIdNode = moduleNode.get("moduleId");
        if (moduleIdNode == null || moduleIdNode.isNull() || moduleIdNode.asText().isBlank()) {
            context.state.addError(
                    modulePath,
                    null,
                    "moduleId",
                    ValidationErrorCodes.MODULE_ID_REQUIRED,
                    "模块 ID 不能为空");
            return;
        }
        String moduleId = moduleIdNode.asText();

        JsonNode recordsNode = moduleNode.get("record");
        if (recordsNode == null || !recordsNode.isArray()) {
            context.state.addError(
                    modulePath,
                    moduleId,
                    "record",
                    ValidationErrorCodes.RECORD_REQUIRED,
                    "record 必须是数组");
            return;
        }

        for (int i = 0; i < recordsNode.size(); i++) {
            if (context.allRecords.size() >= context.options.maxRecords()) {
                reportMaxRecords(modulePath, context);
                return;
            }

            String recordPath = modulePath + ".record[" + i + "]";
            JsonNode recordNode = recordsNode.get(i);
            if (recordNode == null || !recordNode.isObject()) {
                context.state.addError(
                        recordPath,
                        moduleId,
                        null,
                        ValidationErrorCodes.INVALID_REQUEST,
                        "记录必须是对象");
                continue;
            }

            ObjectNode row = readRow(recordNode, moduleId, recordPath, context.state);
            RecordNode node = new RecordNode(moduleId, i, recordPath, row, parent);
            if (parent == null) {
                context.roots.add(node);
            } else {
                parent.children().add(node);
            }
            context.allRecords.add(node);

            JsonNode childrenNode = recordNode.get("children");
            if (childrenNode == null || childrenNode.isNull()) {
                continue;
            }
            if (!childrenNode.isArray()) {
                context.state.addError(
                        recordPath + ".children",
                        moduleId,
                        "children",
                        ValidationErrorCodes.INVALID_REQUEST,
                        "children 必须是数组");
                continue;
            }
            for (int j = 0; j < childrenNode.size(); j++) {
                buildModule(
                        childrenNode.get(j),
                        node,
                        recordPath + ".children[" + j + "]",
                        depth + 1,
                        context);
            }
        }
    }

    private static ObjectNode readRow(
            JsonNode recordNode, String moduleId, String recordPath, ValidationState state) {
        JsonNode rowNode = recordNode.get("row");
        if (rowNode == null || !rowNode.isObject()) {
            state.addError(
                    recordPath,
                    moduleId,
                    "row",
                    ValidationErrorCodes.ROW_REQUIRED,
                    "记录的 row 必须是 JSON 对象");
            return JsonNodeFactory.instance.objectNode();
        }
        return (ObjectNode) rowNode;
    }

    private static void reportMaxRecords(String modulePath, BuildContext context) {
        if (context.maxRecordsReported) {
            return;
        }
        context.maxRecordsReported = true;
        context.state.addError(
                modulePath,
                null,
                null,
                ValidationErrorCodes.MAX_RECORDS_EXCEEDED,
                "记录数量超过限制 " + context.options.maxRecords());
    }

    private static final class BuildContext {
        private final ValidationState state;
        private final ValidationOptions options;
        private final List<RecordNode> roots = new ArrayList<>();
        private final List<RecordNode> allRecords = new ArrayList<>();
        private boolean maxRecordsReported;

        private BuildContext(ValidationState state, ValidationOptions options) {
            this.state = state;
            this.options = options;
        }
    }
}
