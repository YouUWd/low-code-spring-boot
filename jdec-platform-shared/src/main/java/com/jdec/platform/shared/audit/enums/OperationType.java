package com.jdec.platform.shared.audit.enums;

import lombok.Getter;

/** 操作类型枚举 */
@Getter
public enum OperationType {
    CREATE("新增"),
    UPDATE("修改"),
    DELETE("删除");

    private final String description;

    OperationType(String description) {
        this.description = description;
    }
}
