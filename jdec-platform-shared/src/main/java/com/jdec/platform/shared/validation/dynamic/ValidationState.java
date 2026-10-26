package com.jdec.platform.shared.validation.dynamic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 校验状态：收集错误，并记录已校验失败的字段。 */
public final class ValidationState {

    private final List<ValidationError> errors = new ArrayList<>();
    private final Set<String> invalidFields = new HashSet<>();
    private final ValidationOptions options;

    public ValidationState(ValidationOptions options) {
        this.options = options == null ? ValidationOptions.defaults() : options;
    }

    public ValidationOptions options() {
        return options;
    }

    public void addError(
            String path, String moduleId, String fieldKey, String code, String message) {
        errors.add(new ValidationError(path, moduleId, fieldKey, code, message));
        if (fieldKey != null) {
            invalidFields.add(key(path, moduleId, fieldKey));
        }
    }

    public boolean isInvalid(RecordNode record, String fieldKey) {
        return invalidFields.contains(key(record.path(), record.moduleId(), fieldKey));
    }

    public List<ValidationError> errors() {
        return List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    private String key(String path, String moduleId, String fieldKey) {
        return path + "|" + moduleId + "|" + fieldKey;
    }
}
