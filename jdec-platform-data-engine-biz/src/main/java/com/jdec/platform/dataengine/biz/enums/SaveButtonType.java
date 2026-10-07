package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum SaveButtonType {
    SAVE("save", "保存"),
    CHANGE("change_save", "变更"),
    REAL_TIME_CHANGE("realTime_save", "实时保存"),
    INSERT_AND_GET_ID("insertAndGetId", "获取插入ID"),
    DELETE_BY_ID("deleteById", "单行删除"),
    ;

    private final String key;
    private final String title;

    SaveButtonType(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public static SaveButtonType getSaveButtonTypeByKey(String keyStr) {
        for (SaveButtonType buttonType : SaveButtonType.values()) {
            if (buttonType.key.equals(keyStr)) {
                return buttonType;
            }
        }
        return null;
    }
}
