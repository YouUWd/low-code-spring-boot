package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum SaveType {
    NORMAL_SAVE("save"),
    INSERT_AND_GET_ID("insert_and_get_id"),
    DELETE_BY_ID("delete_by_id"),

    APPROVAL_SAVE("approval_save"),
    APPROVAL_CHANGE("approval_change"),
    APPROVAL_REALTIME("realtime_save"),
    APPROVAL_INSERT_AND_GET_ID("approval_insert_and_get_id"),
    APPROVAL_DELETE_BY_ID("approval_delete_by_id"),
    ;

    private String btn;

    private SaveType(String btn) {
        this.btn = btn;
    }
}
