package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum SaveType {
    SAVE("save"),
    APPROVAL_CHANGE("change_save"),
    APPROVAL_REALTIME("realtime_save"),
    INSERT_AND_GET_ID("insert_and_get_id"),
    DELETE_BY_ID("delete_by_id"),
    ;

    private String btn;

    private SaveType(String btn) {
        this.btn = btn;
    }
}
