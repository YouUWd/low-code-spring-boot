package com.jdec.platform.dataengine.biz.enums;

public enum OpTypeEnum {
    SUBMIT("submit"),
    REJECT_SUBMIT("reject-submit"),
    CHANGE_SUBMIT("change-submit"),
    PASS("pass"),
    REJECT("reject"),
    CANCEL("cancel");

    private String key;

    private OpTypeEnum(String key) {
        this.key = key;
    }

    public static OpTypeEnum getByKey(String key) {
        for (OpTypeEnum opType : values()) {
            if (opType.key.equals(key)) {
                return opType;
            }
        }
        return null;
    }
}
