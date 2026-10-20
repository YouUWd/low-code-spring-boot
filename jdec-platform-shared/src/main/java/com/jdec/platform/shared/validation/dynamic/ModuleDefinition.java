package com.jdec.platform.shared.validation.dynamic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模块字段目录。 */
public final class ModuleDefinition {

    private final String moduleId;
    private final List<FieldDefinition> fields;
    private final Map<String, FieldDefinition> fieldMap;

    public ModuleDefinition(String moduleId, Collection<FieldDefinition> fields) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId 不能为空");
        }
        this.moduleId = moduleId;
        this.fields = fields == null ? List.of() : List.copyOf(fields);

        Map<String, FieldDefinition> map = new LinkedHashMap<>();
        for (FieldDefinition field : this.fields) {
            if (!moduleId.equals(field.moduleId())) {
                throw new IllegalArgumentException(
                        "字段 " + field.fieldKey() + " 的 moduleId 与模块 " + moduleId + " 不一致");
            }
            if (map.putIfAbsent(field.fieldKey(), field) != null) {
                throw new IllegalArgumentException(
                        "模块 " + moduleId + " 存在重复字段: " + field.fieldKey());
            }
        }
        this.fieldMap = Collections.unmodifiableMap(map);
    }

    public String moduleId() {
        return moduleId;
    }

    public List<FieldDefinition> fields() {
        return fields;
    }

    public FieldDefinition findField(String fieldKey) {
        return fieldMap.get(fieldKey);
    }
}
