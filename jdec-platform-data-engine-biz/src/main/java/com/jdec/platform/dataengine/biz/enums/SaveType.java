package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum SaveType {
    NORMAL_SAVE("save"),
    APPROVAL_SAVE("approval_save"),
    APPROVAL_CHANGE("approval_change"),
    APPROVAL_REALTIME("realtime_save"),
    ;

    private String btn;

    private SaveType(String btn) {
        this.btn = btn;
    }
}
