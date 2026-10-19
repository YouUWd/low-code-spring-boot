package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 动态嵌套数据校验入口。
 *
 * <p>业务系统的典型用法：
 *
 * <pre>{@code
 * @PostMapping("/validate")
 * public ApiResponse<ValidationResult> validate(@RequestBody DynamicSaveReq req) {
 *     return ApiResponse.success(dynamicRequestValidator.validate(req));
 * }
 * }</pre>
 *
 * <p>校验通过后，前端再调用 data-engine 的 {@code /save} 接口。校验器与保存流程完全分离。
 */
@Component
public class DynamicRequestValidator {

    private final ModuleDefinitionRegistry registry;
    private final ObjectMapper objectMapper;

    public DynamicRequestValidator(ModuleDefinitionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(Object request) {
        return validate(request, ValidationOptions.defaults());
    }

    public ValidationResult validate(Object request, ValidationOptions options) {
        JsonNode requestNode =
                request instanceof JsonNode node ? node : objectMapper.valueToTree(request);
        return validate(requestNode, options);
    }

    public ValidationResult validate(JsonNode request, ValidationOptions options) {
        ValidationOptions effectiveOptions =
                options == null ? ValidationOptions.defaults() : options;
        ValidationState state = new ValidationState(effectiveOptions);
        RequestTree tree = RequestTreeBuilder.build(request, state, effectiveOptions);

        for (RecordNode node : tree.allRecords()) {
            ModuleDefinition definition = registry.get(node.moduleId());
            if (definition == null) {
                state.addError(
                        node.path(),
                        node.moduleId(),
                        null,
                        ValidationErrorCodes.UNKNOWN_MODULE,
                        "未知模块: " + node.moduleId());
                continue;
            }
            validateRecord(node, tree, definition, state);
        }

        return ValidationResult.of(state.errors());
    }

    private void validateRecord(
            RecordNode node, RequestTree tree, ModuleDefinition definition, ValidationState state) {
        ObjectNode row = node.row();
        if (row == null) {
            state.addError(
                    node.path(),
                    node.moduleId(),
                    "row",
                    ValidationErrorCodes.ROW_REQUIRED,
                    "记录的 row 必须是 JSON 对象");
            return;
        }

        ValidationContext context = new ValidationContext(tree, node, state, registry);
        ConditionContext conditionContext = new ConditionContext(tree, node, state, registry);

        validateSubmittedFields(node, definition, state);

        for (FieldDefinition field : definition.fields()) {
            ApplicabilityResult applicability =
                    (field.applicability() == null
                                    ? AlwaysApplicable.INSTANCE
                                    : field.applicability())
                            .evaluate(conditionContext);

            JsonNode value = row.get(field.fieldKey());
            boolean missing = value == null;

            switch (applicability.status()) {
                case APPLICABLE -> validateApplicableField(field, value, missing, context, state);
                case NOT_APPLICABLE -> {
                    if (!missing && state.options().rejectNotApplicableFields()) {
                        state.addError(
                                node.path(),
                                node.moduleId(),
                                field.fieldKey(),
                                ValidationErrorCodes.FIELD_NOT_APPLICABLE,
                                "当前条件下不允许提交该字段");
                    }
                }
                case UNKNOWN ->
                        state.addError(
                                node.path(),
                                node.moduleId(),
                                field.fieldKey(),
                                ValidationErrorCodes.APPLICABILITY_UNKNOWN,
                                "无法确定字段是否适用：" + applicability.reason());
            }
        }
    }

    /** 请求 -> 定义：检查未知字段和只读字段。 */
    private void validateSubmittedFields(
            RecordNode node, ModuleDefinition definition, ValidationState state) {
        ValidationOptions options = state.options();
        if (!options.rejectUnknownFields() && !options.rejectNotWritableFields()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = node.row().fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            FieldDefinition field = definition.findField(entry.getKey());
            if (field == null) {
                if (options.rejectUnknownFields()) {
                    state.addError(
                            node.path(),
                            node.moduleId(),
                            entry.getKey(),
                            ValidationErrorCodes.UNKNOWN_FIELD,
                            "当前模块不支持该字段");
                }
                continue;
            }
            if (!field.writable() && options.rejectNotWritableFields()) {
                state.addError(
                        node.path(),
                        node.moduleId(),
                        entry.getKey(),
                        ValidationErrorCodes.FIELD_NOT_WRITABLE,
                        "该字段不允许客户端提交");
            }
        }
    }

    private void validateApplicableField(
            FieldDefinition field,
            JsonNode value,
            boolean missing,
            ValidationContext context,
            ValidationState state) {
        if (missing) {
            if (!skipRequiredCheck(state.options())) {
                validateMissing(field, context, state);
            }
            return;
        }

        if (!value.isNull() && !field.valueType().matches(value)) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.INVALID_TYPE,
                    "字段类型不正确，期望 " + field.valueType());
            return;
        }

        for (FieldRule rule : field.rules()) {
            rule.validate(value, field, context, state);
        }
    }

    private boolean skipRequiredCheck(ValidationOptions options) {
        return options.mode() == ValidationMode.DRAFT || options.mode() == ValidationMode.UPDATE;
    }

    private void validateMissing(
            FieldDefinition field, ValidationContext context, ValidationState state) {
        boolean required = field.rules().stream().anyMatch(rule -> rule instanceof RequiredRule);
        if (required) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.REQUIRED,
                    "缺少必填字段");
        }
    }
}
